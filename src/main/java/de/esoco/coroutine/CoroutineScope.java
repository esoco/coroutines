//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'coroutines' project.
// Copyright 2018 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
package de.esoco.coroutine;

import de.esoco.lib.collection.CollectionUtil;
import de.esoco.lib.concurrent.RunLock;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.obrel.type.MetaTypes;

import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.Coroutines.closeManagedResources;


/********************************************************************
 * A scope that manages one or more running coroutines. A new scope is created
 * through the factory method {@link #launch(CoroutineContext, ScopeCode)}. It
 * receives an instance of the functional interface {@link ScopeCode} and blocks
 * the invoking thread until all started coroutines have finished execution
 * (either successfully or with an exception).
 *
 * <p>The scope will also automatically close all ({@link AutoCloseable})
 * resources that are stored in this scope in relations with the annotation
 * {@link MetaTypes#MANAGED}.</p>
 *
 * @author eso
 */
public class CoroutineScope extends CoroutineEnvironment
{
	//~ Instance fields --------------------------------------------------------

	private CoroutineContext rContext;

	private final AtomicLong nRunningCoroutines = new AtomicLong();
	private final RunLock    aScopeLock		    = new RunLock();
	private CountDownLatch   aFinishSignal	    = new CountDownLatch(1);

	private boolean bCancelOnError = true;
	private boolean bCancelled     = false;

	private Collection<Suspension<?>> aSuspensions = new LinkedHashSet<>();

	private Collection<Continuation<?>> aFailedContinuations =
		new LinkedHashSet<>();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rContext The context to run the scope's coroutines in
	 */
	CoroutineScope(CoroutineContext rContext)
	{
		this.rContext =
			rContext != null ? rContext : Coroutines.getDefaultContext();
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Creates a new scope for the launching of coroutines in the {@link
	 * Coroutines#getDefaultContext() default context}.
	 *
	 * @param rCode The code to execute in the scope
	 *
	 * @see   #launch(CoroutineContext, ScopeCode)
	 */
	public static void launch(ScopeCode rCode)
	{
		launch(null, rCode);
	}

	/***************************************
	 * Creates a new scope for the launching of coroutine executions in a
	 * specific context. This method will block the invoking thread until all
	 * coroutines launched by the argument builder have terminated.
	 *
	 * <p>If one or more of the coroutines or the scope code throw an exception
	 * this method will throw a {@link CoroutineScopeException} as soon as all
	 * other coroutines have terminated. By default an error causes all other
	 * coroutines to be cancelled but that can be changed with {@link
	 * #setCancelOnError(boolean)}. If any other coroutines fail after the first
	 * error their continuations will also be added to the exception.</p>
	 *
	 * @param  rContext The coroutine context for the scope
	 * @param  rCode    The code to execute in the scope
	 *
	 * @throws CoroutineScopeException If one or more of the executed coroutines
	 *                                 failed
	 */
	public static void launch(CoroutineContext rContext, ScopeCode rCode)
	{
		CoroutineScope aScope = new CoroutineScope(rContext);

		aScope.context().scopeLaunched(aScope);

		try
		{
			rCode.runIn(aScope);
		}
		catch (Exception e)
		{
			throw new CoroutineScopeException(e, aScope.aFailedContinuations);
		}
		finally
		{
			// even on errors wait for all asynchronous invocations
			// and finish the scope
			aScope.await();
			aScope.context().scopeFinished(aScope);
			closeManagedResources(aScope, aScope.get(EXCEPTION_HANDLER));
		}

		handleErrors(aScope.aFailedContinuations);
	}

	/***************************************
	 * Handles errors that occurred during the coroutine executions in a scope.
	 *
	 * @param rFailedContinuations A collection of failed continuations (can be
	 *                             empty)
	 */
	private static void handleErrors(
		Collection<Continuation<?>> rFailedContinuations)
	{
		if (rFailedContinuations.size() > 0)
		{
			if (rFailedContinuations.size() == 1)
			{
				Throwable eError =
					CollectionUtil.firstElementOf(rFailedContinuations)
								  .getError();

				if (eError instanceof CoroutineException)
				{
					throw (CoroutineException) eError;
				}
				else
				{
					throw new CoroutineScopeException(rFailedContinuations);
				}
			}
			else
			{
				throw new CoroutineScopeException(rFailedContinuations);
			}
		}
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Starts the asynchronous execution of a coroutine in this scope with an
	 * input value of NULL. This is typically used to start coroutines with a
	 * void input type.
	 *
	 * @param  rCoroutine The coroutine
	 *
	 * @return The continuation of the executing coroutine
	 */
	public <I, O> Continuation<O> async(Coroutine<I, O> rCoroutine)
	{
		return rCoroutine.runAsync(this, null);
	}

	/***************************************
	 * Starts the asynchronous execution of a coroutine in this scope.
	 *
	 * @param  rCoroutine The coroutine
	 * @param  rInput     The coroutine input
	 *
	 * @return The continuation of the executing coroutine
	 */
	public <I, O> Continuation<O> async(Coroutine<I, O> rCoroutine, I rInput)
	{
		return rCoroutine.runAsync(this, rInput);
	}

	/***************************************
	 * Blocks until the coroutines of all {@link CoroutineScope scopes} in this
	 * context have finished execution. If no coroutines are running or all have
	 * finished execution already this method returns immediately.
	 */
	public void await()
	{
		try
		{
			aFinishSignal.await();
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}
	}

	/***************************************
	 * Executes a coroutine in this scope with an input value of NULL and blocks
	 * the current thread until it is finished. This is typically used to start
	 * coroutines with a void input type.
	 *
	 * @param  rCoroutine The coroutine
	 *
	 * @return The continuation of the completed coroutine
	 */
	public <I, O> Continuation<O> blocking(Coroutine<I, O> rCoroutine)
	{
		return rCoroutine.runBlocking(this, null);
	}

	/***************************************
	 * Executes a coroutine in this scope and blocks the current thread until it
	 * is finished.
	 *
	 * @param  rCoroutine The coroutine
	 * @param  rInput     The coroutine input
	 *
	 * @return The continuation of the completed coroutine
	 */
	public <I, O> Continuation<O> blocking(Coroutine<I, O> rCoroutine, I rInput)
	{
		return rCoroutine.runBlocking(this, rInput);
	}

	/***************************************
	 * Cancels the execution of all coroutines that are currently running in
	 * this scope.
	 */
	public void cancel()
	{
		aScopeLock.runLocked(
			() ->
			{
				bCancelled = true;

				for (Suspension<?> rSuspension : aSuspensions)
				{
					rSuspension.cancel();
				}

				aSuspensions.clear();
			});
	}

	/***************************************
	 * Returns the context in which coroutines of this scope are executed.
	 *
	 * @return The coroutine context
	 */
	public CoroutineContext context()
	{
		return rContext;
	}

	/***************************************
	 * Checks this scope and the {@link CoroutineContext} for a channel with the
	 * given ID. If no such channel exists it will be created in this scope. If
	 * a context channel is needed instead it needs to be created in advance
	 * with {@link CoroutineContext#createChannel(ChannelId, int)}.
	 *
	 * @see CoroutineEnvironment#getChannel(ChannelId)
	 */
	@Override
	public <T> Channel<T> getChannel(ChannelId<T> rId)
	{
		if (rContext.hasChannel(rId))
		{
			return rContext.getChannel(rId);
		}
		else
		{
			return super.getChannel(rId);
		}
	}

	/***************************************
	 * Returns the number of currently running coroutines. This will only be a
	 * momentary value as the execution of the coroutines happens asynchronously
	 * and coroutines may finish while querying this count.
	 *
	 * @return The number of running coroutines
	 */
	public long getCoroutineCount()
	{
		return nRunningCoroutines.get();
	}

	/***************************************
	 * Checks whether a channel with the given ID exists in this scope or in the
	 * {@link CoroutineContext}.
	 *
	 * @see CoroutineEnvironment#hasChannel(ChannelId)
	 */
	@Override
	public boolean hasChannel(ChannelId<?> rId)
	{
		return super.hasChannel(rId) || rContext.hasChannel(rId);
	}

	/***************************************
	 * Checks whether this scope has been cancelled.
	 *
	 * @return TRUE if cancelled
	 */
	public boolean isCancelled()
	{
		return bCancelled;
	}

	/***************************************
	 * Checks whether the execution of the other coroutines in this scope is
	 * canceled if an exception occurs in a coroutine. Can be changed with
	 * {@link #setCancelOnError(boolean)}.
	 *
	 * @return TRUE if all coroutines are cancelled if a coroutine fails
	 */
	public boolean isCancelOnError()
	{
		return bCancelOnError;
	}

	/***************************************
	 * Non-blockingly checks whether this scope has finished execution of all
	 * coroutines. Due to the asynchronous nature of coroutine executions this
	 * method will only return when preceded by a blocking call like {@link
	 * #await()}.
	 *
	 * @return TRUE if finished
	 */
	public boolean isFinished()
	{
		return aFinishSignal.getCount() == 0;
	}

	/***************************************
	 * Removes a channel from this scope or from the {@link CoroutineContext}.
	 * If it exists in both it will only be removed from this scope.
	 *
	 * @see CoroutineEnvironment#removeChannel(ChannelId)
	 */
	@Override
	public void removeChannel(ChannelId<?> rId)
	{
		if (hasChannel(rId))
		{
			super.removeChannel(rId);
		}
		else
		{
			rContext.removeChannel(rId);
		}
	}

	/***************************************
	 * Sets the behavior on coroutine errors in the scope. If set to TRUE (which
	 * is the default) any exception in a coroutine will cancel the execution of
	 * this scope. If FALSE all other coroutines are allowed to finish execution
	 * (or fail too) before the scope's execution is finished. In any case the
	 * scope will throw a {@link CoroutineScopeException} if one or more errors
	 * occurred.
	 *
	 * @param bCancel TRUE to cancel running coroutine if an error occurs; FALSE
	 *                to let them finish
	 */
	public void setCancelOnError(boolean bCancel)
	{
		bCancelOnError = bCancel;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return String.format(
			"%s[%d]",
			getClass().getSimpleName(),
			nRunningCoroutines);
	}

	/***************************************
	 * Adds a suspension of a coroutine in this scope.
	 *
	 * @param rSuspension The suspension to add
	 */
	void addSuspension(Suspension<?> rSuspension)
	{
		aScopeLock.runLocked(
			() ->
		{
			if (bCancelled)
			{
				rSuspension.cancel();
			}
			else
			{
				aSuspensions.add(rSuspension);
			}
		});
	}

	/***************************************
	 * Removes a continuation from the list of failed continuations to prevent
	 * an error exception upon completion.
	 *
	 * @param rContinuation The continuation to remove
	 */
	void continuationErrorHandled(Continuation<?> rContinuation)
	{
		aFailedContinuations.remove(rContinuation);
	}

	/***************************************
	 * Notifies this context that a coroutine execution has been finished
	 * (either regularly or by canceling).
	 *
	 * @param rContinuation The continuation of the execution
	 */
	void coroutineFinished(Continuation<?> rContinuation)
	{
		if (nRunningCoroutines.decrementAndGet() == 0)
		{
			aFinishSignal.countDown();
		}
	}

	/***************************************
	 * Notifies this context that a coroutine has been started in it.
	 *
	 * @param rContinuation The continuation of the execution
	 */
	void coroutineStarted(Continuation<?> rContinuation)
	{
		if (nRunningCoroutines.incrementAndGet() == 1 &&
			aFinishSignal.getCount() == 0)
		{
			aFinishSignal = new CountDownLatch(1);
		}
	}

	/***************************************
	 * Signals this scope that an error occurred during a certain coroutine
	 * execution. This will cancel the execution of all coroutines in this scope
	 * and throw a {@link CoroutineScopeException} from the {@link
	 * #launch(ScopeCode)} methods.
	 *
	 * @param rContinuation The continuation that failed with an exception
	 */
	void fail(Continuation<?> rContinuation)
	{
		aScopeLock.runLocked(
			() ->
			{
				aFailedContinuations.add(rContinuation);

				if (bCancelOnError && !bCancelled)
				{
					cancel();
				}

				coroutineFinished(rContinuation);
			});
	}

	/***************************************
	 * Adds a suspension of a coroutine in this scope.
	 *
	 * @param rSuspension The suspension to add
	 */
	void removeSuspension(Suspension<?> rSuspension)
	{
		aScopeLock.runLocked(
			() ->
		{
			if (!bCancelled)
			{
				aSuspensions.remove(rSuspension);
			}
		});
	}

	//~ Inner Interfaces -------------------------------------------------------

	/********************************************************************
	 * A functional interface that will be executed in a scope that has been
	 * launched with {@link CoroutineScope#launch(ScopeCode)}. It is typically
	 * used in form of a lambda expression or method reference.
	 *
	 * @author eso
	 */
	@FunctionalInterface
	public interface ScopeCode
	{
		//~ Methods ------------------------------------------------------------

		/***************************************
		 * Starts coroutines in the given {@link CoroutineScope} by invoking
		 * methods like {@link CoroutineScope#async(Coroutine)} on it and
		 * optionally also performs other operations, like processing the
		 * results.
		 *
		 * @param  rScope The scope to run in
		 *
		 * @throws Exception Executions may throw arbitrary exceptions which
		 *                   will be handled by the scope
		 */
		public void runIn(CoroutineScope rScope) throws Exception;
	}
}
