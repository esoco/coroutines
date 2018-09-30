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

import de.esoco.coroutine.Coroutine.Subroutine;

import de.esoco.lib.concurrent.RunLock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import org.obrel.core.RelatedObject;
import org.obrel.core.RelationType;

import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.Coroutines.closeManagedResources;


/********************************************************************
 * A continuation represents the state of a coroutine execution. It can be used
 * to carry state between coroutine execution steps by setting relations on it.
 * The method {@link Coroutine#then(java.util.function.BiFunction)} gives the
 * code of a step access to the current continuation it is running in.
 *
 * <p>This class also implements the {@link Future} interface and can therefore
 * be used like any other Java future. The only limitation is that due to the
 * cooperative concurrency of coroutines it is not possible to immediately
 * interrupt a coroutine execution. Therefore the boolean parameter of the
 * method {@link #cancel(boolean)} is ignored.</p>
 *
 * <p>If a continuation has been cancelled all blocking {@link Future} methods
 * will throw a {@link CancellationException} after the wait lock is
 * removed.</p>
 *
 * @author eso
 */
public class Continuation<T> extends RelatedObject implements Executor
{
	//~ Instance fields --------------------------------------------------------

	private final CoroutineScope rScope;

	private T		  rResult    = null;
	private boolean   bCancelled = false;
	private boolean   bFinished  = false;
	private Throwable eError     = null;

	private Deque<Coroutine<?, ?>> aCoroutineStack = new ArrayDeque<>();

	private Function<T, ?> fRunWhenDone;

	private final CountDownLatch aFinishSignal		 = new CountDownLatch(1);
	private final RunLock		 aPostProcessingLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance for the execution of the given {@link Coroutine}
	 * in a certain scope.
	 *
	 * @param rScope     The coroutine context
	 * @param rCoroutine The coroutine that is executed with this continuation
	 */
	public Continuation(CoroutineScope rScope, Coroutine<?, T> rCoroutine)
	{
		this.rScope = rScope;

		aCoroutineStack.push(rCoroutine);
		rScope.coroutineStarted(this);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Awaits the completion of this continuation. This is just a semantic
	 * variant of {@link #getResult()} which ignores the result value.
	 *
	 * @return This instance to allow further invocations
	 */
	public Continuation<T> await()
	{
		getResult();

		return this;
	}

	/***************************************
	 * Cancels the execution of the associated {@link Coroutine} at the next
	 * suspension point. Due to the nature of the cooperative concurrency of
	 * coroutines there is no guarantee as to when the cancellation will occur.
	 * The bMayInterruptIfRunning parameter is ignored because the thread on
	 * which the current step is running is not known.
	 */
	public void cancel()
	{
		if (!bFinished)
		{
			bCancelled = true;
			finish(null);
		}
	}

	/***************************************
	 * Returns the context of the executed coroutine.
	 *
	 * @return The coroutine context
	 */
	public final CoroutineContext context()
	{
		return rScope.context();
	}

	/***************************************
	 * Forwards the execution to the executor of the {@link CoroutineContext}.
	 *
	 * @see Executor#execute(Runnable)
	 */
	@Override
	public void execute(Runnable rCommand)
	{
		context().getExecutor().execute(rCommand);
	}

	/***************************************
	 * Signals that an error occurred during the coroutine execution. This will
	 * set this continuation to canceled and makes the error exception available
	 * through {@link #getError()}. It will also invoke {@link
	 * CoroutineScope#fail(Continuation)} on the scope this continuation runs
	 * in.
	 *
	 * @param eError The exception that caused the error
	 */
	public void fail(Throwable eError)
	{
		if (!bFinished)
		{
			this.eError = eError;
			rScope.fail(this);
			cancel();

			getConfiguration(EXCEPTION_HANDLER, null).accept(eError);
		}
	}

	/***************************************
	 * Duplicated here for easier access during coroutine execution.
	 *
	 * @see CoroutineContext#getChannel(ChannelId)
	 */
	public final <C> Channel<C> getChannel(ChannelId<C> rId)
	{
		return context().getChannel(rId);
	}

	/***************************************
	 * Returns either the root coroutine or, if subroutines have been started
	 * from it, the currently executing subroutine.
	 *
	 * @return The currently executing coroutine
	 */
	public final Coroutine<?, ?> getCurrentCoroutine()
	{
		return aCoroutineStack.peek();
	}

	/***************************************
	 * Returns the error exception that caused a coroutine cancelation.
	 *
	 * @return The error or NULL for none
	 */
	public Throwable getError()
	{
		return eError;
	}

	/***************************************
	 * A variant of {@link #get()} to access the coroutine execution result
	 * without throwing a checked exception. If this continuation has been
	 * cancelled a {@link CancellationException} will be thrown.
	 *
	 * @return The result
	 */
	public T getResult()
	{
		try
		{
			aFinishSignal.await();

			if (bCancelled)
			{
				if (eError != null)
				{
					throw new CoroutineException(eError);
				}
				else
				{
					throw new CancellationException();
				}
			}

			return rResult;
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}
	}

	/***************************************
	 * Returns the value of a runtime state relation of the current execution.
	 * This will first look for the value in currently executing coroutine
	 * (either the root or a subroutine). If not found there the value will be
	 * queried from this continuation first and if not there too, from the
	 * scope. To the a runtime state value the respective relation needs to be
	 * set on the appropriate stage (coroutine, continuation, scope).
	 *
	 * @param  rConfigType The state relation type
	 * @param  rDefault    The default value if no state relation exists
	 *
	 * @return The runtime state value (may be null)
	 */
	public <V> V getState(RelationType<V> rConfigType, V rDefault)
	{
		Coroutine<?, ?> rCoroutine = getCurrentCoroutine();
		V			    rValue     = rDefault;

		if (rCoroutine.hasRelation(rConfigType))
		{
			rValue = rCoroutine.get(rConfigType);
		}
		else if (hasRelation(rConfigType))
		{
			rValue = get(rConfigType);
		}
		else if (rScope.hasRelation(rConfigType))
		{
			rValue = rScope.get(rConfigType);
		}

		return rValue;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	public boolean isCancelled()
	{
		return bCancelled || rScope.isCancelled();
	}

	/***************************************
	 * {@inheritDoc}
	 */
	public boolean isFinished()
	{
		return bFinished;
	}

	/***************************************
	 * Returns the scope in which the coroutine is executed.
	 *
	 * @return The coroutine scope
	 */
	public final CoroutineScope scope()
	{
		return rScope;
	}

	/***************************************
	 * Suspends a step for later invocation and returns an instance of {@link
	 * Suspension} that contains the state necessary for resuming the execution.
	 * Other than {@link #suspend(Continuation, Object)} this suspension will
	 * not contain an explicit input value. Such suspensions are used if the
	 * input will only become available when the suspension ends (e.g. when
	 * receiving data asynchronously).
	 *
	 * @param  rStep The step to suspend
	 *
	 * @return A new suspension object
	 */
	public <I> Suspension<I> suspend(CoroutineStep<I, ?> rStep)
	{
		return suspend(rStep, null);
	}

	/***************************************
	 * Suspends a step for later invocation and returns an instance of {@link
	 * Suspension} that contains the state necessary for resuming the execution.
	 * If the input value is not known before the suspension ends the method
	 * {@link #suspend(Continuation)} can be used instead.
	 *
	 * @param  rStep  The step to suspend
	 * @param  rInput The input value for the execution
	 *
	 * @return A new suspension object
	 */
	public <I> Suspension<I> suspend(CoroutineStep<I, ?> rStep, I rInput)
	{
		return new Suspension<>(rInput, rStep, this);
	}

	/***************************************
	 * Sets a function that will be invoked after the coroutine has successfully
	 * finished execution and {@link #finish(Object)} has been invoked. The
	 * given code will always be run asynchronously after the execution has
	 * finished. If the execution of the coroutine is cancelled (by invoking
	 * {@link #cancel(boolean)}) the code will not be invoked.
	 *
	 * @param  fRunWhenDone The function to apply when the execution has
	 *                      finished
	 *
	 * @return This instance to allow further invocations like {@link #get()} or
	 *         {@link #await()}
	 */
	public Continuation<T> then(Function<T, ?> fRunWhenDone)
	{
		// lock ensures that fRunWhenDone is not set while finishing is in progress
		aPostProcessingLock.runLocked(
			() ->
		{
			if (bFinished)
			{
				if (!bCancelled)
				{
					CompletableFuture.runAsync(
						() -> fRunWhenDone.apply(getResult()));
				}
			}
			else
			{
				this.fRunWhenDone = fRunWhenDone;
			}
		});

		return this;
	}

	/***************************************
	 * Returns the value of a configuration relation. The lookup has the
	 * precedence <i>continuation (this) -&gt; scope -&gt; context -&gt;
	 * coroutine</i>, meaning that a configuration in an earlier stage overrides
	 * the later ones. This means that a (static) configuration in a coroutine
	 * definition can be overridden by the runtime stages.
	 *
	 * <p>Coroutine steps that want to modify the configuration of the root
	 * coroutine they are running in should set the configuration value on the
	 * the continuation. To limit the change to the currently running coroutine
	 * (e.g. a subroutine) configurations should be set on {@link
	 * Continuation#getCurrentCoroutine()} instead.</p>
	 *
	 * @param  rConfigType The configuraton relation type
	 * @param  rDefault    The default value if no state relation exists
	 *
	 * @return The configuration value (may be NULL)
	 */
	protected <V> V getConfiguration(RelationType<V> rConfigType, V rDefault)
	{
		V rValue = rDefault;

		if (hasRelation(rConfigType))
		{
			rValue = get(rConfigType);
		}
		else if (rScope.hasRelation(rConfigType))
		{
			rValue = rScope.get(rConfigType);
		}
		else if (rScope.context().hasRelation(rConfigType))
		{
			rValue = rScope.context().get(rConfigType);
		}
		else
		{
			Coroutine<?, ?> rCoroutine = getCurrentCoroutine();

			if (rCoroutine.hasRelation(rConfigType))
			{
				rValue = rCoroutine.get(rConfigType);
			}
		}

		return rValue;
	}

	/***************************************
	 * Signals a finished {@link Coroutine} execution. This is invoked
	 * internally by the framework at the end of the execution.
	 *
	 * @param rResult The result of the coroutine execution
	 */
	void finish(T rResult)
	{
		assert aCoroutineStack.size() == 1;

		try
		{
			this.rResult = rResult;

			// lock ensures that setting of fRunWhenDone is correctly synchronized
			aPostProcessingLock.runLocked(() -> bFinished = true);

			aFinishSignal.countDown();

			if (!bCancelled && fRunWhenDone != null)
			{
				CompletableFuture.runAsync(() -> fRunWhenDone.apply(rResult));
			}

			rScope.coroutineFinished(this);
		}
		finally
		{
			Consumer<Throwable> fErrorHandler =
				getConfiguration(EXCEPTION_HANDLER, null);

			closeManagedResources(getCurrentCoroutine(), fErrorHandler);
			closeManagedResources(this, fErrorHandler);
		}
	}

	/***************************************
	 * Removes a subroutine from the coroutines stack when it has finished
	 * execution.
	 */
	void subroutineFinished()
	{
		closeManagedResources(
			getCurrentCoroutine(),
			getConfiguration(EXCEPTION_HANDLER, null));

		aCoroutineStack.pop();
	}

	/***************************************
	 * Pushes a subroutine on the coroutines stack upon execution.
	 *
	 * @param rSubroutine The subroutine
	 */
	void subroutineStarted(Subroutine<?, ?, ?> rSubroutine)
	{
		aCoroutineStack.push(rSubroutine);
	}
}
