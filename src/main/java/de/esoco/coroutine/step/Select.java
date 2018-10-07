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
package de.esoco.coroutine.step;

import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineScope;
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;


/********************************************************************
 * A coroutine step that suspends the coroutine execution until one or more of
 * several asynchronously executed coroutines resumes.
 *
 * @author eso
 */
public class Select<I, O> extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private List<Coroutine<? super I, ? extends O>> aCoroutines =
		new ArrayList<>();

	private Predicate<Continuation<?>> pSelectCritiera = c -> true;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rFromCoroutines The coroutines to select from
	 */
	public Select(Collection<Coroutine<? super I, ? extends O>> rFromCoroutines)
	{
		if (rFromCoroutines.size() == 0)
		{
			throw new IllegalArgumentException(
				"At least one coroutine to select required");
		}

		aCoroutines.addAll(rFromCoroutines);
	}

	/***************************************
	 * Copies the state of another instance.
	 *
	 * @param rOther The other instance
	 */
	private Select(Select<I, O> rOther)
	{
		aCoroutines.addAll(rOther.aCoroutines);

		pSelectCritiera = rOther.pSelectCritiera;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends the coroutine execution until one coroutine finishes and then
	 * resumes the execution with the result. By default the result of the first
	 * finished coroutine is selected. That can be modified by providing a
	 * different selection condition to {@link #until(Predicate)} which will
	 * return a new {@link Select} instance. Modified instances that select from
	 * additional coroutines or steps can be created with {@link #or(Coroutine)}
	 * and {@link #or(CoroutineStep)}.
	 *
	 * @param  rFromCoroutines The coroutines to select from
	 *
	 * @return A new step instance
	 */
	@SafeVarargs
	public static <I, O> Select<I, O> select(
		Coroutine<? super I, ? extends O>... rFromCoroutines)
	{
		return new Select<>(asList(rFromCoroutines));
	}

	/***************************************
	 * Suspends the coroutine execution until one coroutine step finishes. The
	 * step arguments will be wrapped into new coroutines and then handed to
	 * {@link #select(Coroutine...)}.
	 *
	 * @param  rFromSteps The coroutine steps to select from
	 *
	 * @return A new step instance
	 */
	@SafeVarargs
	public static <I, O> Select<I, O> select(
		CoroutineStep<? super I, ? extends O>... rFromSteps)
	{
		return new Select<>(
			asList(rFromSteps).stream()
			.map(rStep -> new Coroutine<>(rStep))
			.collect(Collectors.toList()));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Creates a new instance that selects from an additional coroutine.
	 *
	 * @param  rCoroutine The additional coroutine to select from
	 *
	 * @return The new instance
	 */
	public Select<I, O> or(Coroutine<? super I, ? extends O> rCoroutine)
	{
		Select<I, O> aSelect = new Select<>(this);

		aSelect.aCoroutines.add(rCoroutine);

		return aSelect;
	}

	/***************************************
	 * Creates a new instance that selects from an additional step. The step
	 * will be wrapped into a new coroutine.
	 *
	 * @param  rStep The additional step to select from
	 *
	 * @return The new instance
	 */
	public Select<I, O> or(CoroutineStep<? super I, ? extends O> rStep)
	{
		Select<I, O> aSelect = new Select<>(this);

		aSelect.aCoroutines.add(new Coroutine<>(rStep));

		return aSelect;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<I> fPreviousExecution,
						 CoroutineStep<O, ?>  rNextStep,
						 Continuation<?>	  rContinuation)
	{
		fPreviousExecution.thenAcceptAsync(
			i -> selectAsync(i, rNextStep, rContinuation));
	}

	/***************************************
	 * Adds a condition for the result selection. If a succefully finished
	 * continuation matches the given predicate it will be selected as the step
	 * result.
	 *
	 * @param  pSelectCriteria A condition that checks if a result should be
	 *                         selected
	 *
	 * @return A new step instance
	 */
	public Select<I, O> until(Predicate<Continuation<?>> pSelectCriteria)
	{
		Select<I, O> aSelect = new Select<>(aCoroutines);

		aSelect.pSelectCritiera = pSelectCriteria;

		return aSelect;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected O execute(I rInput, Continuation<?> rContinuation)
	{
		// even if executed blocking the selection must happen asynchronously
		return rContinuation.scope()
							.async(new Coroutine<>(this), rInput)
							.getResult();
	}

	/***************************************
	 * Initiates the asynchronous selection.
	 *
	 * @param rInput        The input value
	 * @param rNextStep     The step to resume after the suspension
	 * @param rContinuation the current continuation
	 */
	void selectAsync(I					 rInput,
					 CoroutineStep<O, ?> rNextStep,
					 Continuation<?>	 rContinuation)
	{
		Selection<O, O, O> aSelection =
			Selection.ofSingleValue(this, rNextStep, rContinuation, c -> true);

		CoroutineScope rScope = rContinuation.scope();

		rContinuation.suspendTo(aSelection);

		aCoroutines.forEach(
			rCoroutine -> { aSelection.add(rScope.async(rCoroutine, rInput)); });
	}
}
