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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.obrel.core.Relatable;
import org.obrel.core.RelatedObject;
import org.obrel.core.RelationType;

import static de.esoco.coroutine.Coroutines.COROUTINE_LISTENERS;
import static de.esoco.coroutine.Coroutines.COROUTINE_STEP_LISTENER;
import static de.esoco.coroutine.Coroutines.COROUTINE_SUSPENSION_LISTENER;
import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.Coroutines.closeManagedResources;


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

	private Function<T, ?> fRunWhenDone;

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
		aStateLock.runLocked(
			() ->
		{
			if (!bFinished)
			{
				bCancelled = true;
				finish(null);
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
	 * Reurn the result of the coroutine execution. If this continuation has
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
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}
	}

	/***************************************
	 * Returns a state value with a default value of NULL.
	 *
	 * @see #getState(RelationType, Object)
	 */
	public <V> V getState(RelationType<V> rConfigType)
	{
		return getState(rConfigType, null);
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
	 * The suspension will not contain an input value because it is typically
	 * not know upon suspendsion. It must be provided later, either when
	 * resuming with {@link Suspension#resume(Object)} or by setting it into the
	 * suspension with {@link Suspension#withInput(Object)}.
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
		// only one suspension per continuation is possible
		assert rCurrentSuspension == null;

		Suspension<V> aSuspension =
			new Suspension<>(rSuspendingStep, rSuspendedStep, this);

		rScope.addSuspension(aSuspension);
		rCurrentSuspension = aSuspension;

		if (fSuspensionListener != null)
		{
			fSuspensionListener.accept(rCurrentSuspension, true);
		}

		return aSuspension;
	}

	/***************************************
	 * Sets a function that will be invoked after the coroutine has successfully
	 * finished execution and {@link #finish(Object)} has been invoked. The
	 * given code will always be run asynchronously after the execution has
	 * finished. If the execution of the coroutine is cancelled (by invoking
	 * {@link #cancel()}) the code will not be invoked.
	 *
	 * @param  fRunWhenDone The function to apply when the execution has
	 *                      finished
	 *
	 * @return This instance to allow further invocations like {@link
	 *         #getResult()} or {@link #await()}
	 */
	public Continuation<T> then(Function<T, ?> fRunWhenDone)
	{
		// lock ensures that fRunWhenDone is not set while finishing is in progress
		aStateLock.runLocked(
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
			aStateLock.runLocked(() -> bFinished = true);

			aFinishSignal.countDown();

			if (!bCancelled && fRunWhenDone != null)
			{
				CompletableFuture.runAsync(() -> fRunWhenDone.apply(rResult));
			}

			rScope.coroutineFinished(this);
			notifyListeners(EventType.FINISHED);
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
	 * Resumes a suspension.
	 *
	 * @param rSuspension The suspension to resume
	 * @param rInput      The input for the resumed step
	 */
	<I> void resumeSuspension(Suspension<I> rSuspension, I rInput)
	{
		assert rCurrentSuspension == rSuspension;

		if (!isCancelled())
		{
			if (fSuspensionListener != null)
			{
				fSuspensionListener.accept(rCurrentSuspension, false);
			}

			CompletableFuture<I> fResume =
				CompletableFuture.supplyAsync(() -> rInput, this);

			// the resume step is always either a StepChain which contains it's
			// own next step or the final step in a coroutine and therefore
			// rNextStep can be NULL
			rSuspension.resumeStep().runAsync(fResume, null, this);
		}

		rCurrentSuspension = null;
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
