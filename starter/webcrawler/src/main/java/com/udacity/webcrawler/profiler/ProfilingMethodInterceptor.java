package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;


/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

    private final Clock clock;
    private final ProfilingState profilingState;

    private final Object delegate;


    ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState profilingState) {
        this.clock = Objects.requireNonNull(clock);
        this.profilingState = Objects.requireNonNull(profilingState);
        this.delegate = Objects.requireNonNull(delegate);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        /*
           This method interceptor should inspect the called method to see if it is a profiled
               method. For profiled methods, the interceptor should record the start time, then
               invoke the method using the object that is being profiled. Finally, for profiled
               methods, the interceptor should record how long the method call took, using the
               ProfilingState methods.
        */

        if (method.isAnnotationPresent(Profiled.class)) {

            // record the start time
            long startTime = clock.millis();

            // invoke the method using the object that is being profiled
            try {
                return method.invoke(delegate, args);
            } catch (IllegalAccessException x) {
                throw new RuntimeException(x);
            } catch (InvocationTargetException ex) {
                // Rethrow so that interceptor should always throw the same Throwable
                throw ex.getTargetException();
            } finally {
                // any @Profiled is time recorded even with exceptions.
                long endTime = clock.millis();
                profilingState.record(delegate.getClass(), method, Duration.ofMillis(endTime - startTime));

            }
        }

        else {

            /*
            Object#equals(Object) should behave correctly.
            If the name of the intercepted method is equals and if the declaring class is java.lang.Object
            */
            if (method.getName().equals("equals")
                    && method.getDeclaringClass() == java.lang.Object.class) {
                return this.delegate.equals(args[0]);
            }

            // otherwise invoke without profiling
            try {
                return method.invoke(delegate, args);
            } catch (IllegalAccessException x) {
                throw new RuntimeException(x);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }
    }


}
