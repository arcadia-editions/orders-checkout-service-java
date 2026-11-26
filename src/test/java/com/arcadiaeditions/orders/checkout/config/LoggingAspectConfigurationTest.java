package com.arcadiaeditions.orders.checkout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.arcadiaeditions.orders.checkout.core.inbound.StockUnavailableException;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.StandardEnvironment;

class LoggingAspectConfigurationTest {

    private static final String ORDER_ID = "ord-1";

    @Test
    void stockUnavailableExceptionIsLoggedAtDebugOutsideDevelopment() {
        var events = capture("test", new StockUnavailableException(ORDER_ID, List.of()));

        assertEquals(1, events.size());
        assertEquals(Level.DEBUG, events.get(0).getLevel());
        assertEquals("Exception in startOrderCheckout() with cause = NULL", events.get(0).getFormattedMessage());
        assertNull(events.get(0).getThrowableProxy());
    }

    @Test
    void stockUnavailableExceptionIsLoggedAtDebugInDevelopment() {
        var thrown = new StockUnavailableException(ORDER_ID, List.of());
        var events = capture(Constants.SPRING_PROFILE_DEVELOPMENT, thrown);

        assertEquals(1, events.size());
        assertEquals(Level.DEBUG, events.get(0).getLevel());
        assertEquals(
                "Exception in startOrderCheckout() with cause = 'NULL' and exception = '" + thrown.getMessage() + "'",
                events.get(0).getFormattedMessage());
        assertEquals(StockUnavailableException.class.getName(), events.get(0).getThrowableProxy().getClassName());
    }

    @Test
    void otherExceptionsStayAtErrorOutsideDevelopment() {
        var thrown = new IllegalStateException("boom", new IllegalArgumentException("root"));
        var events = capture("test", thrown);

        assertEquals(1, events.size());
        assertEquals(Level.ERROR, events.get(0).getLevel());
        assertEquals(
                "Exception in startOrderCheckout() with cause = " + thrown.getCause(),
                events.get(0).getFormattedMessage());
        assertNull(events.get(0).getThrowableProxy());
    }

    @Test
    void otherExceptionsStayAtErrorInDevelopment() {
        var thrown = new IllegalStateException("boom", new IllegalArgumentException("root"));
        var events = capture(Constants.SPRING_PROFILE_DEVELOPMENT, thrown);

        assertEquals(1, events.size());
        assertEquals(Level.ERROR, events.get(0).getLevel());
        assertEquals(
                "Exception in startOrderCheckout() with cause = '" + thrown.getCause() + "' and exception = '"
                        + thrown.getMessage() + "'",
                events.get(0).getFormattedMessage());
        assertEquals(IllegalStateException.class.getName(), events.get(0).getThrowableProxy().getClassName());
    }

    private static List<ILoggingEvent> capture(String profile, Throwable thrown) {
        var env = new StandardEnvironment();
        env.setActiveProfiles(profile);
        var aspect = new LoggingAspectConfiguration.LoggingAspect(env);
        var logger = (Logger) LoggerFactory.getLogger(LoggingAspectConfigurationTest.class);
        var previousLevel = logger.getLevel();
        var additive = logger.isAdditive();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            aspect.logAfterThrowing(new MethodJoinPoint("startOrderCheckout"), thrown);
            return List.copyOf(appender.list);
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(additive);
            appender.stop();
        }
    }

    /** Join point the advice reads: method name and the logger's declaring type. */
    private static final class MethodJoinPoint implements JoinPoint {

        private final String methodName;

        private MethodJoinPoint(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public String toShortString() {
            return methodName + "()";
        }

        @Override
        public String toLongString() {
            return toShortString();
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object[] getArgs() {
            return new Object[0];
        }

        @Override
        public Signature getSignature() {
            return new Signature() {
                @Override
                public String toShortString() {
                    return methodName + "()";
                }

                @Override
                public String toLongString() {
                    return LoggingAspectConfigurationTest.class.getName() + "." + methodName + "()";
                }

                @Override
                public String getName() {
                    return methodName;
                }

                @Override
                public int getModifiers() {
                    return 0;
                }

                @Override
                @SuppressWarnings("rawtypes")
                public Class getDeclaringType() {
                    return LoggingAspectConfigurationTest.class;
                }

                @Override
                public String getDeclaringTypeName() {
                    return LoggingAspectConfigurationTest.class.getName();
                }
            };
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String getKind() {
            return METHOD_EXECUTION;
        }

        @Override
        public StaticPart getStaticPart() {
            return null;
        }
    }
}
