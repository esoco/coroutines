//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-lib' project.
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
import de.esoco.coroutine.CoroutineStep;
import de.esoco.coroutine.Coroutine.Subroutine;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static de.esoco.coroutine.step.CodeExecution.apply;


/********************************************************************
 * A {@link Coroutine} step that executes another coroutine in the context of
 * the parent routine.
 *
 * @author eso
 */
public class CallSubroutine<I, O> extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private final Coroutine<I, O> rCoroutine;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param rCoroutine The sub-coroutine
	 */
	public CallSubroutine(Coroutine<I, O> rCoroutine)
	{
		this.rCoroutine = rCoroutine;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Calls a coroutine as a subroutine of the coroutine this step is added to.
	 *
	 * @param  rCoroutine The coroutine to invoke as a subroutine
	 *
	 * @return The new coroutine step
	 */
	public static <I, O> CallSubroutine<I, O> call(Coroutine<I, O> rCoroutine)
	{
		return new CallSubroutine<>(rCoroutine);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void runAsync(CompletableFuture<I> fPreviousExecution,
						 CoroutineStep<O, ?>  rReturnStep,
						 Continuation<?>	  rContinuation)
	{
		// subroutine needs to be created on invocation because the return step
		// may change between invocations
		new Subroutine<>(rCoroutine, rReturnStep).runAsync(
			fPreviousExecution,
			rContinuation);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected O execute(I rInput, Continuation<?> rContinuation)
	{
		return new Subroutine<>(rCoroutine, apply(Function.identity()))
			   .runBlocking(rInput, rContinuation);
	}
}
