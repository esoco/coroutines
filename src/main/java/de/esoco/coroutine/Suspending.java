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
 * This is a marker interface for coroutine steps that perform suspensions and
 * therefore return a {@link Suspension} object from their implementation of the
 * {@link CoroutineStep#runAsync(java.util.concurrent.CompletableFuture,
 * CoroutineStep, Continuation) runAsync()} method. Other step implementations
 * can require this interface if they only operate on suspendings step.
 *
 * @author eso
 */
public interface Suspending
{
}
