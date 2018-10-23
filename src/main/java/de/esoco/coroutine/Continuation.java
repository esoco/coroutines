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
import de.esoco.coroutine.CoroutineEvent.EventType;

import de.esoco.lib.concurrent.RunLock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.obrel.core.Relatable;
import org.obrel.core.RelatedObject;
import org.obrel.core.RelationType;

import static de.esoco.coroutine.Coroutines.COROUTINE_LISTENERS;
import static de.esoco.coroutine.Coroutines.COROUTINE_STEP_LISTENER;
import static de.esoco.coroutine.Coroutines.COROUTINE_SUSPENSION_LISTENER;
import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.Coroutines.closeManagedResources;

import static org.obrel.type.StandardTypes.NAME;


/********************************************************************
 * A continuation represents the state of a coroutine execution. It can be used
 * to carry state between coroutine execution steps by setting relations on it.
 * The method {@link Coroutine#then(CoroutineStep)} gives the code of a step
 * access to the current continuation it is running in.
 *
 * <p>This class also implements the {@link Future} interface and can therefore
 * be used like any other Java future. The only limitation is that due to the
 * cooperative concurrency of coroutines it is not possible to immediately
 * interrupt a coroutine execution. Therefore the boolean parameter of the
 * method {@link #cancel()} is ignored.</p>
 *
 * <p>If a continuation has been cancelled all blocking {@link Future} methods
 * will throw a {@link CancellationException} after the wait lock is
 * removed.</p>
 *
 * @author eso
 */
public class Continuation<T> extends RelatedObject implements Executor
{
	//~ Static fields/initializers ---------------------------------------------

	private static final AtomicLong aNextId = new AtomicLong(1);

	//~ Instance fields --------------------------------------------------------

	private final CoroutineScope rScope;

	private final long    nId				 = aNextId.getAndIncrement();
	private T			  rResult			 = null;
	private boolean		  bCancelled		 = false;
	private boolean		  bFinished			 = false;
	private Throwable     eError			 = null;
	private Suspension<?> rCurrentSuspension = null;

	private Consumer<Continuation<T>> fRunWhenDone;
	private Consumer<Continuation<T>> fRunOnCancel;
	private Consumer<Continuation<T>> fRunOnError;

	private Deque<Coroutine<?, ?>> aCoroutineStack = new ArrayDeque<>();
	private final CountDownLatch   aFinishSignal   = new CountDownLatch(1);
	private final RunLock		   aStateLock	   = new RunLock();

	BiConsumer<Suspension<?>, Boolean>				 fSuspensionListener = null;
	BiConsumer<CoroutineStep<?, ?>, Continuation<?>> fStepListener		 = null;

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

		fSuspensionListener = getConfiguration(COROUTINE_SUSPENSION_LISTENER);
		fStepListener	    = getConfiguration(COROUTINE_STEP_LISTENER);

		rScope.coroutineStarted(this);
		notifyListeners(EventType.STARTED);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Awaits the completion of this continuation. This is basically a variant
	 * of {@link #getResult()} which ignores the result value.
	 */
	public void await()
	{
		getResult();
	}

	/***************************************
	 * Awaits the completion of this continuation but only until a timeout is
	 * reached. If the timeout is reached before completion the returned value
	 * will be FALSE.
	 *
	 * @param  nTimeout The timeout value
	 * @param  eUnit    The time unit of the the timeout
	 *
	 * @return TRUE if the coroutine has finished, FALSE if the timeout elapsed
	 */
	public boolean await(long nTimeout, TimeUnit eUnit)
	{
		try
		{
			return aFinishSignal.await(nTimeout, eUnit);
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}
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
		aStateLock.runLocked(
			() ->
		{
			if (!bFinished)
			{
				bCancelled = true;
				finish(null);

				if (fRunOnCancel != null)
				{
					fRunOnCancel.accept(this);
				}
			}
		});

		if (rCurrentSuspension != null)
		{
			rCurrentSuspension.cancel();
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
	 * Marks an error of this continuation as handled. This will remove this
	 * instance from the failed continuations of the scope and thus prevent the
	 * scope from throwing an exception because of this error upon completion.
	 *
	 * @throws IllegalStateException If this instance has no error
	 */
	public void errorHandled()
	{
		if (eError == null)
		{
			throw new IllegalStateException("No error exists");
		}

		rScope.continuationErrorHandled(this);
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
	 * @param  eError The exception that caused the error
	 *
	 * @return Declared as Void so that it can be used in calls to {@link
	 *         CompletableFuture#exceptionally(Function)} without the need to
	 *         return a (NULL) value
	 */
	public Void fail(Throwable eError)
	{
		if (!bFinished)
		{
			this.eError = eError;
			rScope.fail(this);
			cancel();

			getConfiguration(EXCEPTION_HANDLER, null).accept(eError);

			if (fRunOnError != null)
			{
				fRunOnError.accept(this);
			}
		}

		return null;
	}

	/***************************************
	 * Duplicated here for easier access during coroutine execution.
	 *
	 * @see CoroutineScope#getChannel(ChannelId)
	 */
	public final <C> Channel<C> getChannel(ChannelId<C> rId)
	{
		return rScope.getChannel(rId);
	}

	/***************************************
	 * Returns a configuration value with a default value of NULL.
	 *
	 * @see #getConfiguration(RelationType, Object)
	 */
	public <V> V getConfiguration(RelationType<V> rConfigType)
	{
		return getConfiguration(rConfigType, null);
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
	public <V> V getConfiguration(RelationType<V> rConfigType, V rDefault)
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

			// if rDefault is NULL always query the relation to also get
			// default and initial values
			if (rDefault == null || rCoroutine.hasRelation(rConfigType))
			{
				rValue = rCoroutine.get(rConfigType);
			}
		}

		return rValue;
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
	 * Returns the current suspension.
	 *
	 * @return The current suspension or NULL for none
	 */
	public final Suspension<?> getCurrentSuspension()
	{
		return rCurrentSuspension;
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
	 * Return the result of the coroutine execution. If this continuation has
	 * been cancelled a {@link CancellationException} will be thrown. If it has
	 * failed with an error a {@link CoroutineException} will be thrown.
	 *
	 * @return The result
	 */
	public T getResult()
	{
		try
		{
			aFinishSignal.await();
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}

		return getResultImpl();
	}

	/***************************************
	 * Return the result of the coroutine execution or throws a {@link
	 * CoroutineException} if a timeout is reached. If this continuation has
	 * been cancelled a {@link CancellationException} will be thrown. If it has
	 * failed with an error a {@link CoroutineException} will be thrown.
	 *
	 * @param  nTimeout The timeout value
	 * @param  eUnit    The time unit of the the timeout
	 *
	 * @return The result The result of the execution
	 *
	 * @throws CoroutineException    If the timeout has elapsed before finishing
	 *                               or an error occurred
	 * @throws CancellationException If the coroutine had been cancelled
	 */
	public T getResult(long nTimeout, TimeUnit eUnit)
	{
		try
		{
			if (!aFinishSignal.await(nTimeout, eUnit))
			{
				throw new CoroutineException("Timeout reached");
			}
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}

		return getResultImpl();
	}

	/***************************************
	 * Returns a state value with a default value of NULL.
	 *
	 * @see #getState(RelationType, Object)
	 */
	public <V> V getState(RelationType<V> rStateType)
	{
		return getState(rStateType, null);
	}

	/***************************************
	 * Returns the value of a runtime state relation of the current execution.
	 * This will first look for the value in currently executing coroutine
	 * (either the root or a subroutine). If not found there the value will be
	 * queried from this continuation first and if not there too, from the
	 * scope. To the a runtime state value the respective relation needs to be
	 * set on the appropriate stage (coroutine, continuation, scope).
	 *
	 * @param  rStateType The state relation type
	 * @param  rDefault   The default value if no state relation exists
	 *
	 * @return The runtime state value (may be null)
	 */
	public <V> V getState(RelationType<V> rStateType, V rDefault)
	{
		Coroutine<?, ?> rCoroutine = getCurrentCoroutine();
		V			    rValue     = rDefault;

		if (rCoroutine.hasRelation(rStateType))
		{
			rValue = rCoroutine.get(rStateType);
		}
		else if (hasRelation(rStateType))
		{
			rValue = get(rStateType);
		}
		else if (rScope.hasRelation(rStateType))
		{
			rValue = rScope.get(rStateType);
		}

		return rValue;
	}

	/***************************************
	 * Returns the unique ID of this instance.
	 *
	 * @return The continuation ID
	 */
	public final long id()
	{
		return nId;
	}

	/***************************************
	 * Checks if the execution of the coroutine has been cancelled. If it has
	 * been cancelled because of and error the method {@link #getError()} will
	 * return an exception.
	 *
	 * @return TRUE if the execution has been cancelled
	 */
	public boolean isCancelled()
	{
		return bCancelled;
	}

	/***************************************
	 * Checks if the execution of the coroutine has finished. Whether it has
	 * finished successfully or by cancelation can be checked with {@link
	 * #isCancelled()}. If it has been cancelled because of and error the method
	 * {@link #getError()} will return an exception.
	 *
	 * @return TRUE if the execution has finished
	 */
	public boolean isFinished()
	{
		return bFinished;
	}

	/***************************************
	 * Sets a function to be run if the execution of this instance is cancelled.
	 *
	 * @param  fRunOnCancel A function to be run on cancellation
	 *
	 * @return This instance to allow additional invocations
	 */
	public Continuation<T> onCancel(Consumer<Continuation<T>> fRunOnCancel)
	{
		// ensure that function is not set while cancel is in progress
		aStateLock.runLocked(
			() ->
		{
			if (bCancelled && eError == null)
			{
				fRunOnCancel.accept(this);
			}
			else
			{
				this.fRunOnCancel = fRunOnCancel;
			}
		});

		return this;
	}

	/***************************************
	 * Sets a function to be run if the execution of this instance fails.
	 *
	 * @param  fRunOnError A function to be run on cancellation
	 *
	 * @return This instance to allow additional invocations
	 */
	public Continuation<T> onError(Consumer<Continuation<T>> fRunOnError)
	{
		// ensure that function is not set while cancel is in progress
		aStateLock.runLocked(
			() ->
		{
			if (bCancelled && eError != null)
			{
				fRunOnError.accept(this);
			}
			else
			{
				this.fRunOnError = fRunOnError;
			}
		});

		return this;
	}

	/***************************************
	 * Sets a function that will be invoked after the coroutine has successfully
	 * finished execution and {@link #finish(Object)} has been invoked. If the
	 * execution of the coroutine is cancelled (by invoking {@link #cancel()})
	 * the code will not be invoked. The code will be run directly, not
	 * asynchronously.
	 *
	 * @param  fRunWhenDone The consumer to process this continuation with when
	 *                      the execution has finished
	 *
	 * @return This instance to allow additional invocations
	 */
	public Continuation<T> onFinish(Consumer<Continuation<T>> fRunWhenDone)
	{
		// ensure that function is not set while finishing is in progress
		aStateLock.runLocked(
			() ->
			{
				this.fRunWhenDone = fRunWhenDone;

				if (bFinished && !bCancelled)
				{
					fRunWhenDone.accept(this);
				}
			});

		return this;
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
	 * Suspends an invoking step for later invocation. Returns an instance of
	 * {@link Suspension} that contains the state necessary for resuming the
	 * execution. The suspension will not contain an input value because it is
	 * typically not know upon suspension. It must be provided later, either
	 * when resuming with {@link Suspension#resume(Object)} or by setting it
	 * into the suspension with {@link Suspension#withValue(Object)}.
	 *
	 * @param  rSuspendingStep The step initiating the suspension
	 * @param  rSuspendedStep  The step to suspend
	 *
	 * @return A new suspension object
	 */
	public <V> Suspension<V> suspend(
		CoroutineStep<?, V> rSuspendingStep,
		CoroutineStep<V, ?> rSuspendedStep)
	{
		return suspendTo(
			new Suspension<>(rSuspendingStep, rSuspendedStep, this));
	}

	/***************************************
	 * Suspends an invoking step for later invocation with the given instance of
	 * a suspension subclass. This method is only intended for special
	 * suspension cases. Most step implementations should call {@link
	 * #suspend(CoroutineStep, CoroutineStep)} instead.
	 *
	 * @param  rSuspension The suspension to suspend to
	 *
	 * @return The suspension object
	 */
	public <V> Suspension<V> suspendTo(Suspension<V> rSuspension)
	{
		// only one suspension per continuation is possible
		assert rCurrentSuspension == null;

		rScope.addSuspension(rSuspension);
		rCurrentSuspension = rSuspension;

		if (fSuspensionListener != null)
		{
			fSuspensionListener.accept(rCurrentSuspension, true);
		}

		return rSuspension;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return String.format(
			"%s-%d[%s]",
			getCurrentCoroutine().get(NAME),
			nId,
			rResult);
	}

	/***************************************
	 * Signals a finished {@link Coroutine} execution. This is invoked
	 * internally by the framework at the end of the execution.
	 *
	 * @param rResult The result of the coroutine execution
	 */
	void finish(T rResult)
	{
		assert !bFinished;
		assert aCoroutineStack.size() == 1;

		try
		{
			this.rResult = rResult;

			// lock ensures that setting of fRunWhenDone is correctly synchronized
			aStateLock.runLocked(() -> bFinished = true);

			aFinishSignal.countDown();

			rScope.coroutineFinished(this);
			notifyListeners(EventType.FINISHED);

			if (!bCancelled && fRunWhenDone != null)
			{
				fRunWhenDone.accept(this);
			}
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
	 * execution. This will also close all managed resources stored in the
	 * coroutine.
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

	/***************************************
	 * Gets notified by {@link Suspension#resume(Object)} upon resuming.
	 *
	 * @param rSuspension The suspension to resume
	 */
	<I> void suspensionResumed(Suspension<I> rSuspension)
	{
		assert rCurrentSuspension == rSuspension;

		if (!isCancelled())
		{
			if (fSuspensionListener != null)
			{
				fSuspensionListener.accept(rCurrentSuspension, false);
			}
		}

		rCurrentSuspension = null;
	}

	/***************************************
	 * Traces the execution of coroutine steps (typically for debugging
	 * purposes). Invokes the listener provided in the relation with the type
	 * {@link Coroutines#COROUTINE_STEP_LISTENER} if it is not NULL.
	 *
	 * @param rStep The step to trace
	 */
	final void trace(CoroutineStep<?, ?> rStep)
	{
		if (fStepListener != null)
		{
			fStepListener.accept(rStep, this);
		}
	}

	/***************************************
	 * Internal implementation of querying the the result.
	 *
	 * @return The result
	 */
	private T getResultImpl()
	{
		if (bCancelled)
		{
			if (eError != null)
			{
				if (eError instanceof CoroutineException)
				{
					throw (CoroutineException) eError;
				}
				else
				{
					throw new CoroutineException(eError);
				}
			}
			else
			{
				throw new CancellationException();
			}
		}

		return rResult;
	}

	/***************************************
	 * Notifies the coroutine listeners that are registered in the coroutine,
	 * the scope, and the context.
	 *
	 * @param eType The event type
	 */
	private void notifyListeners(EventType eType)
	{
		Relatable[] rSources =
			new Relatable[] { getCurrentCoroutine(), rScope, rScope.context() };

		for (Relatable rSource : rSources)
		{
			if (rSource.hasRelation(COROUTINE_LISTENERS))
			{
				rSource.get(COROUTINE_LISTENERS)
					   .dispatch(new CoroutineEvent(this, eType));
			}
		}
	}
}
