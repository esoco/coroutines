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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/********************************************************************
 * A {@link Suspension} subclass that contains other suspensions and resumes
 * execution depending on the state of it's children. The generic type of the
 * child suspensions must be the same as that of the group. Because no
 * declarative type coupling is possible this must be enforced by the code that
 * operated on the group and it's children.
 *
 * @author eso
 */
public class SuspensionGroup<T> extends Suspension<T>
{
	//~ Instance fields --------------------------------------------------------

	private final AtomicBoolean aCompleteFlag = new AtomicBoolean(false);

	private final List<Suspension<?>> aChildren = new ArrayList<>();

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @see Suspension#Suspension(CoroutineStep, CoroutineStep, Continuation)
	 */
	protected SuspensionGroup(CoroutineStep<?, T> rSuspendingStep,
							  CoroutineStep<T, ?> rResumeStep,
							  Continuation<?>	  rContinuation)
	{
		super(rSuspendingStep, rResumeStep, rContinuation);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void cancel()
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.cancel();
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void fail(Throwable eError)
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.fail(eError);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public void resume(T rValue)
	{
		if (!aCompleteFlag.get())
		{
			cancelRemaining();
		}

		super.resume(rValue);
	}

	/***************************************
	 * Adds a child suspension to this group.
	 *
	 * @param rChild The child suspension
	 */
	void add(Suspension<?> rChild)
	{
		if (!aCompleteFlag.get())
		{
			aChildren.add(rChild);
		}
		else
		{
			rChild.cancel();
		}
	}

	/***************************************
	 * Notification from a child suspension when it is cancelled.
	 *
	 * @param rChild The cancelled child suspension
	 */
	void childCancelled(Suspension<?> rChild)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aChildren.remove(rChild);
			cancelRemaining();
			cancel();
		}
	}

	/***************************************
	 * Notification from a child suspension when it failed.
	 *
	 * @param rChild The failed child suspension
	 * @param eError The error exception
	 */
	void childFailed(Suspension<?> rChild, Throwable eError)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aChildren.remove(rChild);
			cancelRemaining();
			fail(eError);
		}
	}

	/***************************************
	 * Notification from a child suspension when it is resumed.
	 *
	 * @param rChild The resumed child suspension
	 */
	@SuppressWarnings("unchecked")
	void childResumed(Suspension<?> rChild)
	{
		if (!aCompleteFlag.getAndSet(true))
		{
			aChildren.remove(rChild);
			cancelRemaining();
			resume((T) rChild.value());
		}
	}

	/***************************************
	 * Cancels all remaining child suspensions with the exception of the
	 * argument child.
	 */
	private void cancelRemaining()
	{
		for (Suspension<?> rSuspension : aChildren)
		{
			rSuspension.cancel();
		}

		aChildren.clear();
	}
}
