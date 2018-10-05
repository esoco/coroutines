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

import static de.esoco.coroutine.Coroutines.SUSPENSION_GROUP;


/********************************************************************
 * Encapsulates the data that represents a suspended {@link Coroutine}. The
 * execution can be resumed by invoking {@link #resume()}.
 *
 * @author eso
 */
public class Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private T rValue;

	private final CoroutineStep<?, T> rSuspendingStep;
	private final CoroutineStep<T, ?> rResumeStep;
	private final Continuation<?>     rContinuation;
	private SuspensionGroup<?>		  rSuspensionGroup;

	private boolean		  bCancelled  = false;
	private final RunLock aCancelLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance. The input value for the resume step is not
	 * provided here because it is typically not available upon suspension
	 * because it will only become available when the suspension is resumed
	 * (e.g. when receiving data). To resume execution with an explicit input
	 * value the method {@link #resume(Object)} can be used. If the resume
	 * should occur at a different time than the availability of the input value
	 * a suspension can be updated by calling {@link #withValue(Object)}. In
	 * that case {@link #resume()} can be used later to resume the execution.
	 *
	 * @param rSuspendingStep The step that initiated the suspension
	 * @param rResumeStep     The step to resume the execution with
	 * @param rContinuation   The continuation of the execution
	 */
	protected Suspension(CoroutineStep<?, T> rSuspendingStep,
						 CoroutineStep<T, ?> rResumeStep,
						 Continuation<?>	 rContinuation)
	{
		this.rSuspendingStep = rSuspendingStep;
		this.rResumeStep     = rResumeStep;
		this.rContinuation   = rContinuation;

		rSuspensionGroup =
			rContinuation.getCurrentCoroutine().get(SUSPENSION_GROUP);

		if (rSuspensionGroup != null)
		{
			rSuspensionGroup.add(this);
		}
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

		if (!bCancelled)
		{
			if (rSuspensionGroup != null)
			{
				rSuspensionGroup.childCancelled(this);
			}

			if (!rContinuation.isCancelled())
			{
				rContinuation.cancel();
			}
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
	 * @param eError The error exception
	 */
	public void fail(Throwable eError)
	{
		if (rSuspensionGroup != null)
		{
			rSuspensionGroup.childFailed(this, eError);
		}

		rContinuation.fail(eError);
	}

	/***************************************
	 * Returns the step to be execute when resuming.
	 *
	 * @return The resume step
	 */
	public final CoroutineStep<T, ?> getResumeStep()
	{
		return rResumeStep;
	}

	/***************************************
	 * Returns the step that initiated this suspension.
	 *
	 * @return The suspending step
	 */
	public final CoroutineStep<?, T> getSuspendingStep()
	{
		return rSuspendingStep;
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
		resume(rValue);
	}

	/***************************************
	 * Resumes the execution of the suspended coroutine with the given value.
	 *
	 * @param rValue The input value to the resumed step
	 */
	public void resume(T rValue)
	{
		if (!bCancelled)
		{
			this.rValue = rValue;

			if (rSuspensionGroup != null)
			{
				rSuspensionGroup.childResumed(this);
				rContinuation.cancel();
			}
			else
			{
				rContinuation.resumeSuspension(this, rValue);
			}
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return String.format(
			"%s[%s -> %s]",
			getClass().getSimpleName(),
			rSuspendingStep,
			rResumeStep);
	}

	/***************************************
	 * Returns the value of this suspension. The value will be used as the input
	 * of the resumed step.
	 *
	 * @return The suspension value
	 */
	public final T value()
	{
		return rValue;
	}

	/***************************************
	 * Sets the suspension value and returns this instance so that it can be
	 * used as an updated argument to method calls.
	 *
	 * @param  rValue The new value
	 *
	 * @return This instance
	 */
	public Suspension<T> withValue(T rValue)
	{
		this.rValue = rValue;

		return this;
	}
}
