# Java Coroutines

This project contains a pure Java implementation of coroutines. I has a single dependency to the [ObjectRelations project](https://github.com/esoco/objectrelations). It can be build locally after cloning by starting a gradle build with `gradlew build`. To include coroutines into a project, add the dependency to your project. In gradle it would look like this:

```gradle
dependencies {
	compile 'de.esoco:coroutines:0.9.0'
}
```

The following gives only a short overview of how to use this project. More detailed information can be found on our [documentation site](https://esoco.gitbook.io/sdack/coroutines/introduction) and in the [generated javadoc](https://esoco.github.io/coroutines/javadoc/).

## Declaring Coroutines

Coroutines are used in two stages: the first is the declaration of coroutines, the second their execution. To declare coroutines this framework provides a simple builder pattern that starts with the invocation of the static factory method [Coroutine.first(CoroutineStep)](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Coroutine.html#first-de.esoco.coroutine.CoroutineStep-). This creates a new coroutine instance that invokes a certain execution step. Afterward the coroutine can be extended with additional steps by invoking the instance method [Coroutine.then(CoroutineStep)](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Coroutine.html#then-de.esoco.coroutine.CoroutineStep-). Each call of `then()` will return a new coroutine instance because coroutines are immutable. That allows to use existing coroutines as templates for derived coroutines.

### Coroutine Steps

The execution steps of a coroutine are instances of the abstract class [CoroutineStep](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/CoroutineStep.html). The sub-package `step` contains several pre-defined step implementations as well as several asynchronous I/O steps in the sub-package`step.nio`. All framework steps have static factory methods that can be used in conjunction with the coroutine builder methods and by applying static imports to declare coroutines with a fluent API. A simple example based on the step [CodeExecution](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/step/CodeExecution.html) which executes functional expressions would look like this:

```java
import static de.esoco.coroutine.Coroutine.*;
import static de.esoco.coroutine.step.CodeExecution.*;

Coroutine<String, Integer> parseInteger =
    Coroutine.first(apply((String s) -> s.trim()))
             .then(apply(s -> Integer.valueOf(s)));
```

## Executing Coroutines

The execution of coroutines follows the pattern of structured concurrency and therefore requires an enclosing [CoroutineScope](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/CoroutineScope.html). Like coroutines the scope provides a static factory method that receives a functional interface implementation which contains the code to run. That code can then run arbitrary coroutines asynchronously in the scope like in this example:

```java
Coroutine<?, ?> crunchNumbers =
    first(run(() -> Range.from(1).to(10).forEach(Math::sqrt)));

CoroutineScope.launch(scope -> {
    // start a million coroutines
    for (int i = 0; i < 1_000_000; i++) {
        crunchNumbers.runAsync(scope);
    }
});
```

Any code that would follow after the scope launch block will only run after all coroutines have finished execution, either regularly or with an error. In the latter case the scope would also throw an exception if at least one coroutine fails.

### Continuation

Each start of a coroutine produces a [Continuation](https://esoco.github.io/coroutines/javadoc/de/esoco/coroutine/Continuation.html) object that can be used to observe and if necessary cancel the asynchronous coroutine execution. If the coroutines has finished execution it provides access to the produced result (if such exists).

### ScopeFuture 

If a coroutine scope needs to produce a value it can be started by means of the `produce` method instead of `launch`. That method returns immediately with an instance of `java.util.concurrent.Future` which can then be used to monitor the scope execution and to query the result or error state after completion. This method can also be used if handling a scope through a future is preferred over a launch block. In any case the execution of the asynchronous code is contained and can be supervised by the application. A simple example:

```java
Future<String> futureResult = 
  produce(scope -> getScopeResult(scope), 
          scope -> someCoroutine.runAsync(scope, "input"));

String result = futureResult.get();
```

## Project Structure

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