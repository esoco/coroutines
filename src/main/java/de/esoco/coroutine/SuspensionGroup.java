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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/********************************************************************
 * A {@link Suspension} subclass that groups multiple continuations and resumes
 * execution depending on their state.
 *
 * @author eso
 */
public class SuspensionGroup<T> extends Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private final AtomicBoolean aCompleteFlag = new AtomicBoolean(false);

	private final List<Continuation<? extends T>> aContinuations =
		new ArrayList<>();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance. Internal, creation through {@link
	 * Continuation#suspendGroup(CoroutineStep, CoroutineStep)}.
	 *
	 * @see Suspension#Suspension(CoroutineStep, CoroutineStep, Continuation)
	 */
	SuspensionGroup(CoroutineStep<?, T> rSuspendingStep,
					CoroutineStep<T, ?> rResumeStep,
					Continuation<?>		rContinuation)
	{
		super(rSuspendingStep, rResumeStep, rContinuation);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Adds a child suspension to this group.
	 *
	 * @param rContinuation The child suspension
	 */
	public void add(Continuation<? extends T> rContinuation)
	{
		if (!aCompleteFlag.get())
		{
			aContinuations.add(rContinuation);
		}
		else
		{
			rContinuation.cancel();
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void cancel()
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.cancel();
	}

	/***************************************
	 * Notification from a child suspension when it is cancelled.
	 *
	 * @param rContinuation The cancelled continuation
	 */
	public void continuationCancelled(Continuation<? extends T> rContinuation)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			cancel();
		}
	}

	/***************************************
	 * Notification from a child suspension when it failed.
	 *
	 * @param rContinuation The failed {@link Continuation}
	 */
	public void continuationFailed(Continuation<? extends T> rContinuation)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			fail(rContinuation.getError());
		}
	}

	/***************************************
	 * Notification from a child suspension when it is resumed.
	 *
	 * @param rContinuation The finished continuation
	 */
	@SuppressWarnings("unchecked")
	public void continuationFinished(Continuation<? extends T> rContinuation)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			resume(rContinuation.getResult());
		}
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void fail(Throwable eError)
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.fail(eError);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void resume(T rValue)
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.resume(rValue);
	}

	/***************************************
	 * Cancels all remaining child suspensions with the exception of the
	 * argument child.
	 */
	private void cancelRemaining()
	{
		for (Continuation<? extends T> rContinuation : aContinuations)
		{
			rContinuation.cancel();
		}

		aContinuations.clear();
	}
}
