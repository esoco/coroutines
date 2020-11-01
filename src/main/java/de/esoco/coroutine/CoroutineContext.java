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

import de.esoco.lib.concurrent.RunLock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/********************************************************************
 * The context for the execution of {@link Coroutine Coroutines}.
 *
 * @author eso
 */
public class CoroutineContext extends CoroutineEnvironment {
    //~ Instance fields --------------------------------------------------------

    private final Executor rExecutor;

    private ScheduledExecutorService rScheduler;

    private final AtomicLong nRunningScopes = new AtomicLong();

    private final RunLock aScopeLock = new RunLock();

    private CountDownLatch aScopesFinishedSignal = new CountDownLatch(1);

    //~ Constructors -----------------------------------------------------------

    /***************************************
     * Creates a new instance that uses the {@link ForkJoinPool#commonPool()
     * common thread pool} as the executor.
     */
    public CoroutineContext() {
        this(ForkJoinPool.commonPool());
    }

    /***************************************
     * Creates a new instance with a specific coroutine executor. If the
     * executor also implements the {@link ScheduledExecutorService} interface
     * it will also be used for scheduling purposes.
     *
     * @param rExecutor The coroutine executor
     */
    public CoroutineContext(Executor rExecutor) {
        this.rExecutor = rExecutor;

        if (rExecutor instanceof ScheduledExecutorService) {
            rScheduler = (ScheduledExecutorService) rExecutor;
        }
    }

    /***************************************
     * Creates a new instance with a specific coroutine executor and scheduler.
     * The latter will be used to execute timed coroutine steps.
     *
     * @param rExecutor  The coroutine executor
     * @param rScheduler The scheduled executor service
     */
    public CoroutineContext(Executor rExecutor,
        ScheduledExecutorService rScheduler) {
        this.rExecutor  = rExecutor;
        this.rScheduler = rScheduler;
    }

    //~ Methods ----------------------------------------------------------------

    /***************************************
     * Blocks until the coroutines of all {@link CoroutineScope scopes} in this
     * context have finished execution. If no coroutines are running or all have
     * finished execution already this method returns immediately.
     */
    public void awaitAllScopes() {
        if (aScopesFinishedSignal != null) {
            try {
                aScopesFinishedSignal.await();
            } catch (InterruptedException e) {
                throw new CoroutineException(e);
            }
        }
    }

    /***************************************
     * Returns the executor to be used for the execution of the steps of a
     * {@link Coroutine}.
     *
     * @return The coroutine executor for this context
     */
    public Executor getExecutor() {
        return rExecutor;
    }

    /***************************************
     * Returns the executor to be used for the execution of timed steps in a
     * {@link Coroutine}. If no scheduler has been set in the constructor or
     * created before a new instance with a pool size of 1 will be created by
     * invoking {@link Executors#newScheduledThreadPool(int)}.
     *
     * @return The coroutine scheduler for this context
     */
    public ScheduledExecutorService getScheduler() {
        if (rScheduler == null) {
            rScheduler = Executors.newScheduledThreadPool(1);
        }
        return rScheduler;
    }

    /***************************************
     * Returns the number of currently active {@link CoroutineScope scopes}.
     * This will only be a momentary value as the execution of the coroutines in
     * the scopes happens asynchronously and some coroutines may finish while
     * querying this count.
     *
     * @return The number of running coroutines
     */
    public long getScopeCount() {
        return nRunningScopes.get();
    }

    /***************************************
     * Notifies this context that a {@link CoroutineScope} has finished
     * executing all coroutines.
     *
     * @param rScope The finished scope
     */
    void scopeFinished(CoroutineScope rScope) {
        if (nRunningScopes.decrementAndGet() == 0) {
            aScopeLock.runLocked(() -> aScopesFinishedSignal.countDown());
        }
    }

    /***************************************
     * Notifies this context that a {@link CoroutineScope} has been launched.
     *
     * @param rScope The launched scope
     */
    void scopeLaunched(CoroutineScope rScope) {
        if (nRunningScopes.incrementAndGet() == 1) {
            aScopeLock.runLocked(() -> {
                if (aScopesFinishedSignal.getCount() == 0) {
                    aScopesFinishedSignal = new CountDownLatch(1);
                }
            });
        }
    }
}
