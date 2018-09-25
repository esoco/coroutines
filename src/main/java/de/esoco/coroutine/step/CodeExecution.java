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
import de.esoco.coroutine.CoroutineStep;
import de.esoco.lib.expression.Functions;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/********************************************************************
 * An coroutine step that executes a code function.
 *
 * @author eso
 */
public class CodeExecution<I, O> extends CoroutineStep<I, O>
{
	//~ Instance fields --------------------------------------------------------

	private final BiFunction<I, Continuation<?>, O> fCode;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance from a binary function that accepts the
	 * continuation of the execution and the input value.
	 *
	 * @param fCode A binary function containing the code to be executed
	 */
	public CodeExecution(BiFunction<I, Continuation<?>, O> fCode)
	{
		Objects.requireNonNull(fCode);
		this.fCode = fCode;
	}

	/***************************************
	 * Creates a new instance from a simple function that processes the input
	 * into the output value.
	 *
	 * @param fCode A function containing the code to be executed
	 */
	public CodeExecution(Function<I, O> fCode)
	{
		Objects.requireNonNull(fCode);
		this.fCode = (i, c) -> fCode.apply(i);
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Applies a {@link Function} to the step input and return the processed
	 * output.
	 *
	 * @param  fCode The function to be executed
	 *
	 * @return A new instance of this class
	 */
	public static <I, O> CodeExecution<I, O> apply(Function<I, O> fCode)
	{
		return new CodeExecution<>(fCode);
	}

	/***************************************
	 * Applies a {@link BiFunction} to the step input and the continuation of
	 * the current execution and return the processed output.
	 *
	 * @param  fCode The binary function to be executed
	 *
	 * @return A new instance of this class
	 */
	public static <I, O> CodeExecution<I, O> apply(
		BiFunction<I, Continuation<?>, O> fCode)
	{
		return new CodeExecution<>(fCode);
	}

	/***************************************
	 * Consumes the input value with a {@link Consumer} and creates no result.
	 *
	 * @param  fCode The consumer to be executed
	 *
	 * @return A new instance of this class
	 */
	public static <T> CodeExecution<T, Void> consume(Consumer<T> fCode)
	{
		return new CodeExecution<>(Functions.consume(fCode));
	}

	/***************************************
	 * Executes a {@link Runnable}, ignoring any input value and returning no
	 * result.
	 *
	 * @param  fCode The runnable to be executed
	 *
	 * @return A new instance of this class
	 */
	public static <T> CodeExecution<T, Void> run(Runnable fCode)
	{
		return new CodeExecution<>(Functions.run(fCode));
	}

	/***************************************
	 * Provides a value from a {@link Supplier} as the result, ignoring any
	 * input value.
	 *
	 * @param  fCode The supplier to be executed
	 *
	 * @return A new instance of this class
	 */
	public static <I, O> CodeExecution<I, O> supply(Supplier<O> fCode)
	{
		return new CodeExecution<>(Functions.supply(fCode));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	protected O execute(I rInput, Continuation<?> rContinuation)
	{
		return fCode.apply(rInput, rContinuation);
	}
}
