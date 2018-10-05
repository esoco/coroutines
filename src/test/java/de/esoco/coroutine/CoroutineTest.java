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

import de.esoco.coroutine.step.Condition;
import de.esoco.coroutine.step.Iteration;
import de.esoco.coroutine.step.Loop;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.junit.BeforeClass;
import org.junit.Test;

import static de.esoco.coroutine.ChannelId.stringChannel;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.Coroutines.COROUTINE_LISTENERS;
import static de.esoco.coroutine.Coroutines.EXCEPTION_HANDLER;
import static de.esoco.coroutine.step.ChannelReceive.receive;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.CodeExecution.run;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Condition.doIf;
import static de.esoco.coroutine.step.Condition.doIfElse;
import static de.esoco.coroutine.step.Iteration.collectEach;
import static de.esoco.coroutine.step.Loop.loopWhile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/********************************************************************
 * Test of {@link Coroutine}.
 *
 * @author eso
 */
public class CoroutineTest
{
	//~ Static fields/initializers ---------------------------------------------

	static Coroutine<String, Integer> CONVERT_INT =
		Coroutine.first(apply((String s) -> s + 5))
				 .then(apply(s -> s.replaceAll("\\D", "")))
				 .then(apply(s -> Integer.valueOf(s)));

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Test class setup.
	 */
	@BeforeClass
	public static void setup()
	{
		// suppress stacktraces from error testing
		Coroutines.getDefaultContext().set(EXCEPTION_HANDLER, t ->{});
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Test of coroutines with a single step.
	 */
	@Test
	public void testCancelSuspension()
	{
		ChannelId<String> ch = stringChannel("TEST_SUSP");

		Coroutine<?, ?> cr = Coroutine.first(receive(ch));

		launch(
			run ->
			{
				// this will block because the channel is never sent to
				Continuation<?> ca = run.async(cr);

				// cancel the scope with the suspended receive
				run.cancel();

				// await async cancelled receive so that states can be checked
				run.await();

				assertTrue(ca.isCancelled());
				assertTrue(ca.isFinished());

				try
				{
					ca.getResult();
					fail();
				}
				catch (CancellationException e)
				{
					// expected
				}
			});
	}

	/***************************************
	 * Test of {@link Condition} step.
	 */
	@Test
	public void testCondition()
	{
		launch(
			run ->
			{
				assertBooleanInput(
					"true",
					"false",
					run,
					Coroutine.first(
						doIfElse(
							b -> b,
							supply(() -> "true"),
							supply(() -> "false"))));

				assertBooleanInput(
					"true",
					"false",
					run,
					Coroutine.first(
						doIf((Boolean b) -> b, supply(() -> "true")).orElse(
							supply(() -> "false"))));

				assertBooleanInput(
					"true",
					"false",
					run,
					Coroutine.first(apply((Boolean b) -> b.toString()))
					.then(apply(s -> Boolean.valueOf(s)))
					.then(
						doIfElse(
							b -> b,
							supply(() -> "true"),
							supply(() -> "false"))));

				assertBooleanInput(
					"true",
					"false",
					run,
					Coroutine.first(apply((Boolean b) -> b.toString()))
					.then(apply(s -> Boolean.valueOf(s)))
					.then(
						doIf((Boolean b) -> b, supply(() -> "true")).orElse(
							supply(() -> "false"))));

				assertBooleanInput(
					"true",
					null,
					run,
					Coroutine.first(doIf(b -> b, supply(() -> "true"))));
			});
	}

	/***************************************
	 * Test of coroutine error handling.
	 */
	@Test
	public void testErrorHandling()
	{
		Coroutine<?, ?> ce =
			Coroutine.first(
				run(() -> { throw new RuntimeException("TEST ERROR"); }));

		try
		{
			launch(run -> run.blocking(ce));
			fail();
		}
		catch (CoroutineScopeException e)
		{
			assertEquals(1, e.getFailedContinuations().size());
		}

		try
		{
			launch(run -> run.async(ce));
			fail();
		}
		catch (CoroutineScopeException e)
		{
			assertEquals(1, e.getFailedContinuations().size());
		}
	}

	/***************************************
	 * Test of {@link Iteration} step.
	 */
	@Test
	public void testIteration()
	{
		Coroutine<String, List<String>> cr =
			Coroutine.first(apply((String s) -> Arrays.asList(s.split(","))))
					 .then(collectEach(apply((String s) ->
		 							s.toUpperCase())));

		launch(
			run ->
			{
				Continuation<?> ca = run.async(cr, "a,b,c,d");
				Continuation<?> cb = run.blocking(cr, "a,b,c,d");

				assertEquals(Arrays.asList("A", "B", "C", "D"), ca.getResult());
				assertEquals(Arrays.asList("A", "B", "C", "D"), cb.getResult());
			});
	}

	/***************************************
	 * Test of coroutines with a single step.
	 */
	@Test
	public void testListener()
	{
		Set<String>				  aEvents = new HashSet<>();
		Coroutine<String, String> cr	  =
			Coroutine.first(apply((String s) -> s.toUpperCase()));

		cr.get(COROUTINE_LISTENERS).add(e -> aEvents.add("CR-" + e.getType()));
		Coroutines.getDefaultContext()
				  .get(COROUTINE_LISTENERS)
				  .add(e -> aEvents.add("CTX-" + e.getType()));

		launch(
			run ->
			{
				run.get(COROUTINE_LISTENERS)
				.add(e -> aEvents.add("SCOPE-" + e.getType()));

				Continuation<String> c = run.async(cr, "test");

				c.await();
				assertTrue(c.isFinished());
			});

		assertTrue(aEvents.contains("CR-STARTED"));
		assertTrue(aEvents.contains("CR-FINISHED"));
		assertTrue(aEvents.contains("SCOPE-STARTED"));
		assertTrue(aEvents.contains("SCOPE-FINISHED"));
		assertTrue(aEvents.contains("CTX-STARTED"));
		assertTrue(aEvents.contains("CTX-FINISHED"));
	}

	/***************************************
	 * Test of the {@link Loop} step.
	 */
	@Test
	public void testLoop()
	{
		Coroutine<Integer, Integer> cr =
			Coroutine.first(loopWhile(i -> i < 10, apply(i -> i + 1)));

		launch(
			run ->
			{
				Continuation<Integer> ca = run.async(cr, 1);
				Continuation<Integer> cb = run.blocking(cr, 1);

				assertEquals(Integer.valueOf(10), ca.getResult());
				assertEquals(Integer.valueOf(10), cb.getResult());
				assertTrue(ca.isFinished());
				assertTrue(cb.isFinished());
			});
	}

	/***************************************
	 * Test of coroutines with multiple steps.
	 */
	@Test
	public void testMultiStep()
	{
		launch(
			run ->
			{
				Continuation<Integer> ca = run.async(CONVERT_INT, "test1234");
				Continuation<Integer> cb = run.blocking(CONVERT_INT, "test1234");

				assertEquals(Integer.valueOf(12345), ca.getResult());
				assertEquals(Integer.valueOf(12345), cb.getResult());
				assertTrue(ca.isFinished());
				assertTrue(cb.isFinished());
			});
	}

	/***************************************
	 * Test of coroutines with a single step.
	 */
	@Test
	public void testSingleStep()
	{
		Coroutine<String, String> cr =
			Coroutine.first(apply((String s) -> s.toUpperCase()));

		launch(
			run ->
			{
				Continuation<String> ca = run.async(cr, "test");
				Continuation<String> cb = run.blocking(cr, "test");

				assertEquals("TEST", ca.getResult());
				assertEquals("TEST", cb.getResult());
				assertTrue(ca.isFinished());
				assertTrue(cb.isFinished());
			});
	}

	/***************************************
	 * Asserts the results of executing a {@link Coroutine} with a boolean
	 * input.
	 *
	 * @param sTrueResult  The expected result for a TRUE input
	 * @param sFalseResult The expected result for a FALSE input
	 * @param run          The coroutine scope
	 * @param cr           The coroutine
	 */
	void assertBooleanInput(String					   sTrueResult,
							String					   sFalseResult,
							CoroutineScope			   run,
							Coroutine<Boolean, String> cr)
	{
		Continuation<String> cat = run.async(cr, true);
		Continuation<String> caf = run.async(cr, false);
		Continuation<String> cbt = run.blocking(cr, true);
		Continuation<String> cbf = run.blocking(cr, false);

		assertEquals(sTrueResult, cat.getResult());
		assertEquals(sTrueResult, cbt.getResult());
		assertEquals(sFalseResult, caf.getResult());
		assertEquals(sFalseResult, cbf.getResult());
		assertTrue(cat.isFinished());
		assertTrue(caf.isFinished());
		assertTrue(cbt.isFinished());
		assertTrue(cbf.isFinished());
	}
}
