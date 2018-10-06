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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;


/********************************************************************
 * A {@link Suspension} subclass that selects the suspension result from one or
 * more of multiple continuations based on certain criteria.
 *
 * @author eso
 */
public class Selection<T> extends Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private Predicate<Continuation<?>> pSelectionComplete;

	private boolean bComplete = false;

	private final List<Continuation<? extends T>> aContinuations =
		new ArrayList<>();

	private final RunLock aCompletionLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Internal constructor to create a new instance. Public creation through
	 * {@link Continuation#suspendSelection(CoroutineStep, CoroutineStep)}.
	 *
	 * @see Suspension#Suspension(CoroutineStep, CoroutineStep, Continuation)
	 */
	Selection(Predicate<Continuation<?>> pSelectionComplete,
			  CoroutineStep<?, T>		 rSuspendingStep,
			  CoroutineStep<T, ?>		 rResumeStep,
			  Continuation<?>			 rContinuation)
	{
		super(rSuspendingStep, rResumeStep, rContinuation);

		this.pSelectionComplete = pSelectionComplete;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Adds a continuation to this group.
	 *
	 * @param rContinuation The continuation
	 */
	public void add(Continuation<? extends T> rContinuation)
	{
		if (!bComplete)
		{
			aContinuations.add(rContinuation);

			rContinuation.onFinish(this::continuationFinished)
						 .onCancel(this::continuationCancelled)
						 .onError(this::continuationFailed);
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
		aCompletionLock.runLocked(
			() ->
		{
			if (!bComplete)
			{
				cancelRemaining();
			}
		});

		super.cancel();
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void fail(Throwable eError)
	{
		aCompletionLock.runLocked(
			() ->
		{
			if (!bComplete)
			{
				cancelRemaining();
			}
		});

		super.fail(eError);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void resume(T rValue)
	{
		aCompletionLock.runLocked(
			() ->
		{
			if (!bComplete)
			{
				cancelRemaining();
			}
		});

		super.resume(rValue);
	}

	/***************************************
	 * Notification from a continuation when it is cancelled.
	 *
	 * @param rContinuation The cancelled continuation
	 */
	void continuationCancelled(Continuation<? extends T> rContinuation)
	{
		if (!bComplete)
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			cancel();
		}
	}

	/***************************************
	 * Notification from a continuation when it failed.
	 *
	 * @param rContinuation The failed {@link Continuation}
	 */
	void continuationFailed(Continuation<? extends T> rContinuation)
	{
		if (!bComplete)
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			fail(rContinuation.getError());
		}
	}

	/***************************************
	 * Notification from a continuation when it is resumed.
	 *
	 * @param rContinuation The finished continuation
	 */
	@SuppressWarnings("unchecked")
	void continuationFinished(Continuation<? extends T> rContinuation)
	{
		if (isCompletedBy(rContinuation))
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			resume(rContinuation.getResult());
		}
	}

	/***************************************
	 * Cancels all remaining continuations.
	 */
	private void cancelRemaining()
	{
		for (Continuation<? extends T> rContinuation : aContinuations)
		{
			rContinuation.cancel();
		}

		aContinuations.clear();
	}

	/***************************************
	 * Checks whether a certain continuation completes this selection.
	 *
	 * @param  rContinuation The continuation to check
	 *
	 * @return TRUE if the selection is completed
	 */
	private boolean isCompletedBy(Continuation<? extends T> rContinuation)
	{
		aCompletionLock.runLocked(
			() ->
		{
			if (!bComplete)
			{
				bComplete = pSelectionComplete.test(rContinuation);
			}
		});

		return bComplete;
	}
}
