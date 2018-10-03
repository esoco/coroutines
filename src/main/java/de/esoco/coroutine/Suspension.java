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


/********************************************************************
 * Encapsulates the data that represents a suspended {@link Coroutine}. The
 * execution can be resumed by invoking {@link #resume()}.
 *
 * @author eso
 */
public class Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private T rInput;

	private final CoroutineStep<?, T> rSuspendingStep;
	private final CoroutineStep<T, ?> rResumeStep;
	private final Continuation<?>     rContinuation;

	private boolean		  bCancelled  = false;
	private final RunLock aCancelLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance. The input value may be NULL if it will only
	 * become available when the suspension is resumed (e.g. when receiving
	 * data). The step may also be NULL if the suspension occurs in the last
	 * step of a coroutine. In that case the {@link #resume()} methods do
	 * nothing but this instance can still be used to store the suspended state
	 * until the suspension ends and the associated action is performed (e.g.
	 * sending data).
	 *
	 * @param rInput          The input value to the step or NULL for none
	 * @param rSuspendingStep The step that initiated the suspension
	 * @param rResumeStep     The step to resume the execution with
	 * @param rContinuation   The continuation of the execution
	 */
	public Suspension(T					  rInput,
					  CoroutineStep<?, T> rSuspendingStep,
					  CoroutineStep<T, ?> rResumeStep,
					  Continuation<?>	  rContinuation)
	{
		this.rInput			 = rInput;
		this.rSuspendingStep = rSuspendingStep;
		this.rResumeStep     = rResumeStep;
		this.rContinuation   = rContinuation;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Cancels this suspension. This will {@link Continuation#cancel() cancel}
	 * the continuation. Tries to resuming a cancelled suspension will be
	 * ignored.
	 */
	public void cancel()
	{
		aCancelLock.runLocked(() -> bCancelled = true);

		if (!rContinuation.isCancelled())
		{
			rContinuation.cancel();
		}
	}

	/***************************************
	 * Returns the continuation of the suspended coroutine.
	 *
	 * @return The continuation
	 */
	public final Continuation<?> continuation()
	{
		return rContinuation;
	}

	/***************************************
	 * Cancels this suspension because of an error. This will {@link
	 * Continuation#fail(Throwable) fail} the continuation. Tries to resume a
	 * failed suspension will be ignored.
	 *
	 * @param e The error exception
	 */
	public void fail(Throwable e)
	{
		rContinuation.fail(e);
	}

	/***************************************
	 * Executes code only if this suspension has not (yet) been cancel. The
	 * given code will be executed with a lock on the cancelation state to
	 * prevent race conditions if other threads try to cancel a suspension while
	 * it is resumed.
	 *
	 * @param fCode The code to execute only if this suspension is not cancelled
	 */
	public void ifNotCancelled(Runnable fCode)
	{
		aCancelLock.runLocked(() ->
		{
			if (!bCancelled)
			{
				fCode.run();
			}
		});
	}

	/***************************************
	 * Returns the input value.
	 *
	 * @return The input
	 */
	public final T input()
	{
		return rInput;
	}

	/***************************************
	 * Checks if the this suspension has been cancelled.
	 *
	 * @return TRUE if the suspension has been cancelled
	 */
	public boolean isCancelled()
	{
		return bCancelled;
	}

	/***************************************
	 * Resumes the execution of the suspended coroutine with the input value
	 * provided to the constructor.
	 *
	 * @see #resume(Object)
	 */
	public final void resume()
	{
		resume(rInput);
	}

	/***************************************
	 * Resumes the execution of the suspended coroutine with the given input
	 * value.
	 *
	 * @param rStepInput The input value to the step
	 */
	public void resume(T rStepInput)
	{
		rContinuation.resumeSuspension(this, rStepInput);
	}

	/***************************************
	 * Returns the step to be execute when resuming.
	 *
	 * @return The resume step
	 */
	public final CoroutineStep<T, ?> resumeStep()
	{
		return rResumeStep;
	}

	/***************************************
	 * Returns the step that initiated this suspension.
	 *
	 * @return The suspending step
	 */
	public final CoroutineStep<?, T> suspendingStep()
	{
		return rSuspendingStep;
	}

	/***************************************
	 * Sets the input value and returns this instance so that it can be used as
	 * an updated argument to method calls.
	 *
	 * @param  rInput The new input value
	 *
	 * @return This instance
	 */
	public Suspension<T> withInput(T rInput)
	{
		this.rInput = rInput;

		return this;
	}
}
