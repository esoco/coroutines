package de.esoco.coroutine;

import de.esoco.coroutine.step.Delay;
import org.junit.jupiter.api.Test;

import static de.esoco.coroutine.CoroutineScope.launch;
import static de.esoco.coroutine.step.CodeExecution.apply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoroutineDelayTest {

	public static final long DELAY = 100L;

	@Test
	public void testDelay() {
		Coroutine<String, String> sleep = Coroutine.first(Delay.sleep(DELAY));

		Coroutine<String, String> r =
			sleep.then(apply((String s) -> s.toUpperCase()));

		launch(scope -> {
			long start = System.currentTimeMillis();
			Continuation<String> ca = r.runAsync(scope, "test");

			assertEquals("TEST", ca.getResult());
			assertTrue((System.currentTimeMillis() - start) > DELAY);
			assertTrue(ca.isFinished());
		});
	}
}
