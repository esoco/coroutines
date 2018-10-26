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
package de.esoco.coroutine.examples;

import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineException;

import de.esoco.lib.logging.Profiler;

import java.util.concurrent.CountDownLatch;

import static de.esoco.coroutine.Coroutine.first;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.run;

import static de.esoco.lib.datatype.Range.from;


/********************************************************************
 * Example of a large-scale parallel execution of coroutines, compare with a
 * large number of threads.
 *
 * @author eso
 */
public class ParallelExecution
{
	//~ Static fields/initializers ---------------------------------------------

	private static final int THREAD_COUNT    = 10_000;
	private static final int COROUTINE_COUNT = 100_000;

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Main
	 *
	 * @param rArgs
	 */
	public static void main(String[] rArgs)
	{
		Coroutine<Void, Void> cr =
			first(run(() -> from(1).to(10).forEach(Math::sqrt)));

		Profiler	   p	  = new Profiler("Parallel Coroutine Execution");
		CountDownLatch signal = new CountDownLatch(THREAD_COUNT);

		for (int i = 0; i < THREAD_COUNT; i++)
		{
			new Thread(
				() ->
			{
				launch(scope ->
				{
					cr.runBlocking(scope);
					signal.countDown();
				});
			}).start();
		}

		try
		{
			signal.await();
		}
		catch (InterruptedException e)
		{
			throw new CoroutineException(e);
		}

		p.measure(THREAD_COUNT + " Threads");

		launch(
			scope ->
		{
			for (int i = 0; i < COROUTINE_COUNT; i++)
			{
				cr.runAsync(scope, null);
			}
		});

		p.measure(COROUTINE_COUNT + " Coroutines");
		p.printSummary();
	}
}
