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
import de.esoco.coroutine.SuspensionGroup;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static de.esoco.coroutine.Coroutines.SUSPENSION_GROUP;

import static java.util.Arrays.asList;


/********************************************************************
 * A coroutine step that suspends the coroutine execution until one of multiple
 * suspendings steps resumes.
 *
 * @author eso
 */
public class Select<I, O> extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private Set<CoroutineStep<I, O>> aSteps = new LinkedHashSet<>();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rFromSteps The steps to select from
	 */
	public Select(Collection<CoroutineStep<I, O>> rFromSteps)
	{
		aSteps.addAll(rFromSteps);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends the coroutine execution until one of multiple suspendings steps
	 * resumes.
	 *
	 * @param  rFromSteps The suspending steps to select from
	 *
	 * @return A new step instance
	 */
	@SafeVarargs
	public static <I, O> Select<I, O> select(CoroutineStep<I, O>... rFromSteps)
	{
		return new Select<>(asList(rFromSteps));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Creates a new instance that selects from an additional step.
	 *
	 * @param  rStep The additional step to select from
	 *
	 * @return
	 */
	public Select<I, O> or(CoroutineStep<I, O> rStep)
	{
		Select<I, O> aSelect = new Select<>(aSteps);

		aSelect.aSteps.add(rStep);

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
			i -> selectAsync(i, rContinuation.suspendGroup(this, rNextStep)));
	}

	/***************************************
	 * @see CoroutineStep#execute(Object, Continuation)
	 */
	@Override
	protected O execute(I rInput, Continuation<?> rContinuation)
	{
		return rContinuation.scope()
							.async(new Coroutine<>(this), rInput)
							.getResult();
	}

	/***************************************
	 * Initiates the asynchronous selection.
	 *
	 * @param rInput           The input value
	 * @param rSuspensionGroup The suspension group
	 */
	void selectAsync(I rInput, SuspensionGroup<O> rSuspensionGroup)
	{
		CoroutineScope rScope = rSuspensionGroup.continuation().scope();

		for (CoroutineStep<I, O> rStep : aSteps)
		{
			rScope.async(
				new Coroutine<>(rStep).with(SUSPENSION_GROUP, rSuspensionGroup),
				rInput);
		}
	}
}
