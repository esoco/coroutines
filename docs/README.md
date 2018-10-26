# Java Coroutines

This project contains a pure Java implementation of coroutines. I has a single dependency to the [ObjectRelations project](https://github.com/esoco/objectrelations). It can be build locally after cloning by starting a gradle build with `gradlew build`. Usage information can be found on our [documentation site](https://esoco.gitbook.io/sdack/coroutines/introduction) and in the [generated javadoc](https://esoco.github.io/coroutines/javadoc/).

The main package of this project is `de.esoco.coroutine`. It contains the core classes of the framework:

* [Coroutine](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Coroutine.html): Serves to declare new coroutines which consist of sequential steps to be executed. Contains the static factory method `first(CoroutinesStep)` to create new declarations and the instance method `then(CoroutineStep)` to extend an existing coroutine. Coroutine instances are always immutable and extending them will always create a new instance.
* [CoroutineStep](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/CoroutineStep.html): The base class for all execution steps in coroutines.
* [CoroutineScope](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/CoroutineScope.html): Following the pattern of structured concurrency, all coroutines must be run from within a coroutine scope. The scope provides a supervision context that enforces the tracking of coroutine and scope termination, either successfully or by cancellation or error.
* [CoroutineContext](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/CoroutineContext.html): Coroutines and scopes are executed in a certain context that provides configuration and defines the threading environment.
* [Continuation](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Continuation.html): The execution of a coroutine is always associated with a dedicated continuation instance that allows the coroutine steps to share state. It also provides access to the scope and context of the respective execution.
* [Suspension](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Suspension.html): Coroutines can be suspended while waiting for resources. This class contains the state associated with the suspension and allows to resume the execution when the suspension condition is resolved.
* [Selection](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Selection.html): A Suspension subclass that suspends on multiple executions and is used for the implementation of selecting steps. 
* [Channel](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Channel.html): This class implements a queue that allow multiple coroutines to perform suspending communication by sending to and receiving from channels. 
* [ChannelId](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/ChannelId.html): An interface that is used for the identification of channels. 

Furthermore the main package contains several exception classes that are used by the framework. All exceptions are unchecked and should therefore always be considered in an application's exception handling. 

The sub-package `step` contains several standard coroutine step implementations. All step classes provides factory methods that can be used for a fluent declaration of coroutines based on the methods  

* [CodeExecution](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/CodeExecution.html): Executes code that is provided as a function as a single coroutine step. It is mainly intended to wrap lambda expressions or method references and provides factory methods based on the different functional interface types of Java: `apply(Function)`, `consume(Consumer)`, `supply(Supplier)`, `run(Runnable)`.
* [CallSubroutine](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/CallSubroutine.html): Runs another coroutine in the continuation of the current one.
* [Condition](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Condition.html): Checks a logical condition by applying a predicate and executes different step according to the result.
* [Loop](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Loop.html): Repeats the execution of a certain step (including subroutines) until a logical condition is met.
* [Iteration](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Iteration.html): Repeats the execution of a certain step (including subroutines) for all element in an `Iterable` input value.
* [Delay](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Delay.html): Suspends the execution of a coroutine for a certain amount of time.
* [Select](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Select.html) and [Collect](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/Collect.html): Suspend the execution of a coroutine until one or all of a set of other coroutines have finished.
* [ChannelSend](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/ChannelSend.html) and [ChannelReceive](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/ChannelReceive.html): These steps suspend the execution of a coroutine until sending to or receiving from a channel succeeded.

 Finally, the sub-package `step.nio` contains several step implementations that use the asynchronous APIs of the `java.nio` package to implement suspending I/O functionality. 
 
The folder *src/examples* contains some examples for using coroutines. 

# License

This project is licensed under the Apache 2.0 license (see LICENSE file for details).  