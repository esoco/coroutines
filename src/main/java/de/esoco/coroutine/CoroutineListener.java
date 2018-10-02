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

/********************************************************************
 * The listener interface for coroutine events.
 *
 * @author eso
 */
public interface CoroutineListener
{
	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Will be notified when a coroutine has finished, either successfully or by
	 * cancelation (including errors).
	 *
	 * @param rContinuation The continuation of the execution
	 */
	public void coroutineFinished(Continuation<?> rContinuation);

	/***************************************
	 * Will be notified when a coroutine is started. The invocation occurs just
	 * before the coroutine will be executed (either asynchronously or
	 * blocking).
	 *
	 * @param rContinuation The continuation of the execution
	 */
	public void coroutineStarted(Continuation<?> rContinuation);
}
