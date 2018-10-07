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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;


/********************************************************************
 * A {@link Suspension} subclass that selects the suspension result from one or
 * more of multiple continuations based on certain criteria.
 *
 * @author eso
 */
public class Selection<T, V, R> extends Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private final CoroutineStep<R, ?>		 rResumeSelectionStep;
	private final Predicate<Continuation<?>> pSelect;
	private final Predicate<Continuation<?>> pComplete;
	private final boolean					 bSingleValue;

	private boolean bComplete = false;
	private List<V> aResults  = new ArrayList<>();

	private final List<Continuation<? extends V>> aContinuations =
		new ArrayList<>();

	private final RunLock aCompletionLock = new RunLock();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Internal constructor to create a new instance. Public creation through
	 * {@link #ofSingleValue(CoroutineStep, CoroutineStep, Continuation)} or
	 * {@link #ofMultipleValues(CoroutineStep, CoroutineStep, Continuation)} for
	 * correct generic typing.
	 *
	 * @param rSuspendingStep The step that initiated the suspension
	 * @param rResumeStep     The step to resume the execution with
	 * @param rContinuation   The continuation of the execution
	 * @param pComplete       The condition for the termination of the selection
	 * @param pSelect         The condition for the selection of results
	 * @param bSingleValue    TRUE for the selection of a single value, FALSE
	 *                        for a collection of values
	 */
	private Selection(CoroutineStep<?, T>		 rSuspendingStep,
					  CoroutineStep<R, ?>		 rResumeStep,
					  Continuation<?>			 rContinuation,
					  Predicate<Continuation<?>> pComplete,
					  Predicate<Continuation<?>> pSelect,
					  boolean					 bSingleValue)
	{
		super(rSuspendingStep, null, rContinuation);

		this.rResumeSelectionStep = rResumeStep;
		this.pSelect			  = pSelect;
		this.pComplete			  = pComplete;
		this.bSingleValue		  = bSingleValue;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Creates a new instance for the selection of a multiple values. If no
	 * values are selected the result will be an empty collection.
	 *
	 * @param  rSuspendingStep The step that initiated the suspension
	 * @param  rResumeStep     The step to resume the execution with
	 * @param  rContinuation   The continuation of the execution
	 * @param  pComplete       The condition for the termination of the
	 *                         selection
	 * @param  pSelect         The condition for the selection of results
	 *
	 * @return The new instance
	 */
	public static <T, V> Selection<T, V, Collection<V>> ofMultipleValues(
		CoroutineStep<?, T>				rSuspendingStep,
		CoroutineStep<Collection<V>, ?> rResumeStep,
		Continuation<?>					rContinuation,
		Predicate<Continuation<?>>		pComplete,
		Predicate<Continuation<?>>		pSelect)
	{
		Selection<T, V, Collection<V>> aSelection =
			new Selection<>(
				rSuspendingStep,
				rResumeStep,
				rContinuation,
				pComplete,
				pSelect,
				false);

		return aSelection;
	}

	/***************************************
	 * Creates a new instance for the selection of a single value. If no value
	 * is selected the result will be NULL.
	 *
	 * @param  rSuspendingStep The step that initiated the suspension
	 * @param  rResumeStep     The step to resume the execution with
	 * @param  rContinuation   The continuation of the execution
	 * @param  pSelect         The condition for the selection of results
	 *
	 * @return The new instance
	 */
	public static <T> Selection<T, T, T> ofSingleValue(
		CoroutineStep<?, T>		   rSuspendingStep,
		CoroutineStep<T, ?>		   rResumeStep,
		Continuation<?>			   rContinuation,
		Predicate<Continuation<?>> pSelect)
	{
		return new Selection<>(
			rSuspendingStep,
			rResumeStep,
			rContinuation,
			c -> true,
			pSelect,
			true);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Adds a continuation to this group.
	 *
	 * @param rContinuation The continuation
	 */
	public void add(Continuation<? extends V> rContinuation)
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
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return String.format(
			"%s[%s -> %s]",
			getClass().getSimpleName(),
			suspendingStep(),
			rResumeSelectionStep);
	}

	/***************************************
	 * Notification from a continuation when it is cancelled.
	 *
	 * @param rContinuation The cancelled continuation
	 */
	void continuationCancelled(Continuation<? extends V> rContinuation)
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
	void continuationFailed(Continuation<? extends V> rContinuation)
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
	void continuationFinished(Continuation<? extends V> rContinuation)
	{
		if (pSelect.test(rContinuation))
		{
			aResults.add(rContinuation.getResult());
		}

		if (isCompletedBy(rContinuation))
		{
			aContinuations.remove(rContinuation);
			cancelRemaining();
			resume();
		}
	}

	/***************************************
	 * Overridden to resume the selection result step instead.
	 *
	 * @see Suspension#resumeAsync(CompletableFuture, Continuation)
	 */
	@Override
	void resumeAsync()
	{
		// type safety is ensured by the factory methods
		@SuppressWarnings("unchecked")
		R rResult =
			(R) (bSingleValue ? (aResults.size() >= 1 ? aResults.get(0)
													  : null) : aResults);

		rResumeSelectionStep.runAsync(
			CompletableFuture.supplyAsync(() -> rResult, continuation()),
			null,
			continuation());
	}

	/***************************************
	 * Cancels all remaining continuations.
	 */
	private void cancelRemaining()
	{
		for (Continuation<? extends V> rContinuation : aContinuations)
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
	private boolean isCompletedBy(Continuation<? extends V> rContinuation)
	{
		aCompletionLock.runLocked(
			() ->
		{
			if (!bComplete)
			{
				bComplete = pComplete.test(rContinuation);
			}
		});

		return bComplete;
	}
}
