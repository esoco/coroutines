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
package de.esoco.coroutine;

import de.esoco.coroutine.ChannelId;
import de.esoco.coroutine.Continuation;
import de.esoco.coroutine.Coroutine;
import de.esoco.coroutine.CoroutineScope;
import de.esoco.coroutine.CoroutineScopeException;
import de.esoco.coroutine.step.Condition;
import de.esoco.coroutine.step.Iteration;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.Test;

import static de.esoco.coroutine.ChannelId.stringChannel;
import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CallSubroutine.call;
import static de.esoco.coroutine.step.ChannelReceive.receive;
import static de.esoco.coroutine.step.ChannelSend.send;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static de.esoco.coroutine.step.CodeExecution.run;
import static de.esoco.coroutine.step.CodeExecution.supply;
import static de.esoco.coroutine.step.Condition.doIf;
import static de.esoco.coroutine.step.Condition.doIfElse;
import static de.esoco.coroutine.step.Iteration.forEach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.obrel.type.StandardTypes.NAME;


/********************************************************************
 * Test of {@link Coroutine}.
 *
 * @author eso
 */
public class CoroutineTest
{
	//~ Static fields/initializers ---------------------------------------------

	private static Coroutine<String, Integer> PARSE_INT =
		Coroutine.first(apply((String s) -> s + 5))
				 .then(apply(s -> s.replaceAll("\\D", "")))
				 .then(apply(s -> Integer.valueOf(s)));

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

				// cancel the receive
				run.cancel();

				// await async receive so that states can be checked
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
	 * Test of asynchronous channel communication.
	 */
	@Test
	public void testChannel()
	{
		ChannelId<String> ch = stringChannel("TEST_CH");

		Coroutine<String, String> cs =
			Coroutine.first(apply((String s) -> s + "test"))
					 .then(send(ch))
					 .with(NAME, "Send");

		Coroutine<?, String> cr1 =
			Coroutine.first(receive(ch))
					 .with(NAME, "Receive")
					 .then(apply(s -> s.toUpperCase()));
		Coroutine<?, String> cr2 =
			cr1.then(apply((String s) -> s.toLowerCase()));

		launch(
			run ->
			{
				Continuation<String> r1 = run.async(cr1);
				Continuation<String> r2 = run.async(cr2);

				Continuation<?> s1 = run.async(cs, "123");
				Continuation<?> s2 = run.async(cs, "456");

				assertEquals("123test", s1.getResult());
				assertEquals("456test", s2.getResult());

				String r1v = r1.getResult();
				String r2v = r2.getResult();

				// because of the concurrent execution it is not fixed which
				// of the values r1 and r2 will receive
				assertTrue(
					"123test".equalsIgnoreCase(r1v) ||
					"456test".equalsIgnoreCase(r1v));
				assertTrue(
					"123test".equalsIgnoreCase(r2v) ||
					"456test".equalsIgnoreCase(r2v));
				assertTrue(s1.isFinished());
				assertTrue(s2.isFinished());
				assertTrue(r1.isFinished());
				assertTrue(r2.isFinished());
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
			Coroutine.first(apply((String s) ->
		 						Arrays.asList(s.split(","))))
					 .then(
		 				forEach(
		 					apply((String s) -> s.toUpperCase()),
		 					() -> new LinkedList<>()));

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
	 * Test of coroutines with multiple steps.
	 */
	@Test
	public void testMultiStep()
	{
		launch(
			run ->
			{
				Continuation<Integer> ca = run.async(PARSE_INT, "test1234");
				Continuation<Integer> cb = run.blocking(PARSE_INT, "test1234");

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
	 * Test of coroutines with a single step.
	 */
	@Test
	public void testSubroutine()
	{
		Coroutine<String, Integer> cr =
			Coroutine.first(call(PARSE_INT)).then(apply(i -> i + 10));

		launch(
			run ->
			{
				Continuation<Integer> ca = run.async(cr, "test1234");
				Continuation<Integer> cb = run.blocking(cr, "test1234");

				assertEquals(Integer.valueOf(12355), ca.getResult());
				assertEquals(Integer.valueOf(12355), cb.getResult());
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
