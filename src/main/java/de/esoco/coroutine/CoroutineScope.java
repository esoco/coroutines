//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-lib' project.
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
 * through the factory method {@link #launch(CoroutineContext, ScopeBuilder)}.
 * It receives an instance of the functional interface {@link ScopeBuilder} and
 * blocks the invoking thread until all started coroutines have finished
 * execution (either successfully or with an exception).
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
	 * @param rBuilder The builder to invoke the launching methods
	 *
	 * @see   #launch(CoroutineContext, ScopeBuilder)
	 */
	public static void launch(ScopeBuilder rBuilder)
	{
		launch(null, rBuilder);
	}

	/***************************************
	 * Creates a new scope for the launching of coroutine executions in a
	 * specific context. This method will block the invoking thread until all
	 * coroutines launched by the argument builder have terminated.
	 *
	 * <p>If one or more of the coroutines do not complete successfully by
	 * throwing an exception this method will also throw a {@link
	 * CoroutineScopeException} as soon as all other coroutines have terminated.
	 * By default an error causes all other coroutines to be cancelled but that
	 * can be changed with {@link #setCancelOnError(boolean)}. If any other
	 * coroutines fail after the first error their continuations will also be
	 * added to the exception.</p>
	 *
	 * @param  rContext The coroutine context for the scope
	 * @param  rBuilder The builder that starts the coroutines
	 *
	 * @throws CoroutineScopeException If one or more of the executed coroutines
	 *                                 failed
	 */
	public static void launch(CoroutineContext rContext, ScopeBuilder rBuilder)
	{
		CoroutineScope rScope = new CoroutineScope(rContext);

		rScope.context().scopeLaunched(rScope);

		rBuilder.buildScope(rScope);
		rScope.await();

		rScope.context().scopeFinished(rScope);

		if (rScope.aFailedContinuations.size() > 0)
		{
			throw new CoroutineScopeException(rScope.aFailedContinuations);
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
	 * #launch(ScopeBuilder)} methods.
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
	 * A functional interface that will be invoked to add coroutine invocations
	 * when creating a new scope with {@link
	 * CoroutineScope#launch(ScopeBuilder)}. Typically used in form of a lambda
	 * expression or method reference.
	 *
	 * @author eso
	 */
	@FunctionalInterface
	public interface ScopeBuilder
	{
		//~ Methods ------------------------------------------------------------

		/***************************************
		 * Builds the {@link Coroutine} invocations in a {@link CoroutineScope}
		 * by invoking methods like {@link CoroutineScope#async(Coroutine)} on
		 * the argument scope.
		 *
		 * @param rScope The scope to build
		 */
		public void buildScope(CoroutineScope rScope);
	}
}
