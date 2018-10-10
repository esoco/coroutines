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
 * more of multiple continuations based on certain criteria. If a child
 * continuation fails the selection step will also fail. If a child continuation
 * is cancelled, it will simply be ignored.
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

	private boolean  bSealed	   = false;
	private boolean  bFinished     = false;
	private Runnable fFinishAction = this::resume;
	private List<V>  aResults	   = new ArrayList<>();

	private final List<Continuation<? extends V>> aContinuations =
		new ArrayList<>();

	private final RunLock aStateLock = new RunLock();

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
	 * Creates a new instance for the selection of multiple values. If no values
	 * are selected the result will be an empty collection.
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
		aStateLock.runLocked(
			() ->
			{
				if (bSealed)
				{
					throw new IllegalStateException("Selection is sealed");
				}

				// first add to make sure remove after an immediate return by
				// the following callbacks is applied
				aContinuations.add(rContinuation);
			});

		rContinuation.onFinish(this::continuationFinished)
					 .onCancel(this::continuationCancelled)
					 .onError(this::continuationFailed);

		if (bFinished)
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
		aStateLock.runLocked(
			() ->
			{
				bFinished     = true;
				fFinishAction = this::cancel;

				checkComplete();
			});
	}

	/***************************************
	 * Seals this instance so that no more coroutines can be added with {@link
	 * #add(Continuation)}. Sealing is necessary to allow the adding of further
	 * coroutines even if previously added coroutines have already finished
	 * execution.
	 */
	public void seal()
	{
		aStateLock.runLocked(() ->
		{
			bSealed = true;
			checkComplete();
		});
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
	 * Notified when a continuation is cancelled. Cancelled continuations will
	 * only be removed but the selection will continue.
	 *
	 * @param rContinuation The finished continuation
	 */
	@SuppressWarnings("unchecked")
	void continuationCancelled(Continuation<? extends V> rContinuation)
	{
		aStateLock.runLocked(
			() ->
			{
				aContinuations.remove(rContinuation);
				checkComplete();
			});
	}

	/***************************************
	 * Notified when the execution of a continuation failed. In that case the
	 * full selection will fail too.
	 *
	 * @param rContinuation The finished continuation
	 */
	@SuppressWarnings("unchecked")
	void continuationFailed(Continuation<? extends V> rContinuation)
	{
		aStateLock.runLocked(
			() ->
			{
				aContinuations.remove(rContinuation);
				bFinished     = true;
				fFinishAction = () -> fail(rContinuation.getError());

				checkComplete();
			});
	}

	/***************************************
	 * Notified when a continuation is finished.
	 *
	 * @param rContinuation The finished continuation
	 */
	@SuppressWarnings("unchecked")
	void continuationFinished(Continuation<? extends V> rContinuation)
	{
		aStateLock.runLocked(
			() ->
			{
				aContinuations.remove(rContinuation);

				if (!bFinished)
				{
					if (pSelect.test(rContinuation))
					{
						aResults.add(rContinuation.getResult());
					}

					bFinished = pComplete.test(rContinuation);
				}

				checkComplete();
			});
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
	 * Resumes this selection if it is sealed and contains no more
	 * continuations.
	 */
	private void checkComplete()
	{
		if (bFinished && !aContinuations.isEmpty())
		{
			// cancel all remaining continuations if already finished; needs to
			// be done with a copied list because cancel may modify the list
			new ArrayList<>(aContinuations).forEach(Continuation::cancel);
		}

		// only finish if all child continuations have finished to race
		// conditions with subsequent step executions
		if (bSealed && aContinuations.isEmpty())
		{
			bFinished = true;

			// will either resume, cancel, or fail this suspension
			fFinishAction.run();
		}
	}
}
