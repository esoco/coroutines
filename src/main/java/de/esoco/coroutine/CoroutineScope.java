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

import de.esoco.lib.concurrent.RunLock;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.obrel.core.RelatedObject;


/********************************************************************
 * A scope that manages one or more running coroutines. A new scope is created
 * through the factory method {@link #launch(CoroutineContext, ScopeCode)}. It
 * receives an instance of the functional interface {@link ScopeCode} and blocks
 * the invoking thread until all started coroutines have finished execution
 * (either successfully or with an exception).
 *
 * @author eso
 */
public class CoroutineScope extends RelatedObject
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
	public static void launch(ScopeCode rBuilder)
	{
		launch(null, rBuilder);
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
		}

		if (aScope.aFailedContinuations.size() > 0)
		{
			throw new CoroutineScopeException(aScope.aFailedContinuations);
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
			throw new CompletionException(e);
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
