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
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Suspending;
import de.esoco.coroutine.Suspension;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;


/********************************************************************
 * A coroutine step that suspends the coroutine execution until one of multiple
 * suspendings steps resumes.
 *
 * @author eso
 */
public class Select<I, O, S extends CoroutineStep<I, O> & Suspending>
	extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private Set<S> aSteps = new LinkedHashSet<>();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rFromSteps The steps to select from
	 */
	public Select(Collection<S> rFromSteps)
	{
		aSteps.addAll(rFromSteps);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Suspends the coroutine execution until one of multiple suspendings steps
	 * resumes.
	 *
	 * @param  rFromStep The first suspending step to select from
	 *
	 * @return A new step instance
	 */
	public static <I, O, S extends CoroutineStep<I, O> & Suspending> Select<I, O, S>
	select(S rFromStep)
	{
		return new Select<>(asList(rFromStep));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public Suspension<O> runAsync(CompletableFuture<I> fPreviousExecution,
								  CoroutineStep<O, ?>  rNextStep,
								  Continuation<?>	   rContinuation)
	{
		SuspensionGroup<O> aSuspension =
			new SuspensionGroup<>(this, rNextStep, rContinuation);

		return aSuspension;
	}

	/***************************************
	 * Always throws an exception because selecting always requires the parallel
	 * and non-blocking execution of the steps to select from. This could be
	 * achieved
	 *
	 * @see CoroutineStep#execute(Object, Continuation)
	 */
	@Override
	protected O execute(I rInput, Continuation<?> rContinuation)
	{
//		CoroutineScope.launch(
//			rContinuation.context(),
//			run ->
//		{
//			for (S rStep : aSteps)
//			{
//				run.async(Coroutine.first(rStep), rInput);
//			}
//		});

		throw new UnsupportedOperationException(
			"Select can only be executed asynchronously");
	}

	//~ Inner Classes ----------------------------------------------------------

	/********************************************************************
	 * TODO: DOCUMENT ME!
	 *
	 * @author eso
	 */
	static class SuspensionGroup<T> extends Suspension<T>
	{
		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance.
		 *
		 * @param rSuspendingStep TODO: DOCUMENT ME!
		 * @param rResumeStep     TODO: DOCUMENT ME!
		 * @param rContinuation   TODO: DOCUMENT ME!
		 */
		public SuspensionGroup(CoroutineStep<?, T> rSuspendingStep,
							   CoroutineStep<T, ?> rResumeStep,
							   Continuation<?>	   rContinuation)
		{
			super(rSuspendingStep, rResumeStep, rContinuation);
		}
	}
}
