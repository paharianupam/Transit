package com.getbeamapp.transit;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.test.ActivityInstrumentationTestCase2;

import com.getbeamapp.transit.prompt.TransitChromeClient;

public class IntegrationTest extends ActivityInstrumentationTestCase2<MainActivity> {

    public final long TIMEOUT = 1000L;

    public IntegrationTest() {
        super(MainActivity.class);
    }

    public void testAllSetup() {
        MainActivity activity = getActivity();
        assertNotNull(activity);

        AndroidTransitContext transit = activity.transit;
        assertNotNull(transit);
        assertNotNull(transit.getAdapter());

        TransitChromeClient adapter = (TransitChromeClient) transit.getAdapter();
        assertNotNull(adapter.getScript());
    }

    public void testTransitInjected() {
        TransitProxy transitExists = getActivity().transit.eval("window.transit != null");
        assertEquals(true, (boolean) transitExists.getBooleanValue());
    }

    public void testException() {
        try {
            getActivity().transit.eval("(void 0).toString()");
            fail();
        } catch (TransitException e) {
            assertEquals("TypeError: Cannot call method 'toString' of undefined", e.getMessage());
        }
    }

    public void testOperationAdd() {
        assertEquals(4, getActivity().transit.eval("2 + 2").getIntegerValue());
    }
    
    public void testContext() {
        AndroidTransitContext transit = getActivity().transit;
        assertEquals(1, transit.evalWithContext("this", 1).getIntegerValue());
        // assertEquals(transit, transit.eval("this"));
    }

    public void testNativeFunction() {
        final boolean[] called = new boolean[] { false };

        AndroidTransitContext transit = getActivity().transit;
        TransitCallable callable = new TransitCallable() {
            @Override
            public Object evaluate(TransitProxy thisArg, TransitProxy... arguments) {
                called[0] = true;
                return 42;
            }
        };

        TransitNativeFunction function = transit.registerCallable(callable);
        TransitProxy result = transit.eval("@()", function);

        assertTrue("Native function not called.", called[0]);
        assertNotNull(result);
        assertEquals(42, (int) result.getIntegerValue());
    }

    public void testNativeArguments() {
        final TransitProxy[] calledWithThis = new TransitProxy[] { null };
        final List<TransitProxy> calledWithArgs = new LinkedList<TransitProxy>();

        AndroidTransitContext transit = getActivity().transit;
        TransitCallable callable = new TransitCallable() {
            @Override
            public Object evaluate(TransitProxy thisArg, TransitProxy... arguments) {
                calledWithThis[0] = thisArg;
                calledWithArgs.addAll(Arrays.asList(arguments));
                return null;
            }
        };

        TransitNativeFunction function = transit.registerCallable(callable);
        transit.eval("@.call(0, 1, 2)", function);

        assertNotNull(calledWithThis[0]);
        assertEquals(2, calledWithArgs.size());

        assertEquals(0, calledWithThis[0].getIntegerValue());
        assertEquals(1, calledWithArgs.get(0).getIntegerValue());
        assertEquals(2, calledWithArgs.get(1).getIntegerValue());
    }

    public void testNativeIdentity() {
        final List<TransitProxy> calledWithArgs = new LinkedList<TransitProxy>();

        AndroidTransitContext transit = getActivity().transit;

        TransitNativeFunction function = transit.registerCallable(new TransitCallable() {
            @Override
            public Object evaluate(TransitProxy thisArg, TransitProxy... arguments) {
                calledWithArgs.addAll(Arrays.asList(arguments));
                return null;
            }
        });
        
        transit.eval("@(@, @)", function, function, function);

        assertEquals(2, calledWithArgs.size());
        assertEquals(function, calledWithArgs.get(0));
        assertEquals(calledWithArgs.get(0), calledWithArgs.get(1));
    }

    public void testNativeRecursion() {
        final int calls = 100; // Change to higher value if needed (1000 calls
                               // require ~8s on my machine)

        final AndroidTransitContext transit = getActivity().transit;
        final TransitCallable callable = new TransitCallable() {
            @Override
            public Object evaluate(TransitProxy thisArg, TransitProxy... arguments) {
                int v = arguments[0].getIntegerValue();

                if (v < calls) {
                    return thisArg.eval(transit.jsExpressionFromCode("f(@ + 1)", v));
                } else {
                    return v;
                }
            }
        };

        final TransitNativeFunction function = transit.registerCallable(callable);
        assertEquals(calls, transit.eval("window.f = @; f(0)", function).getIntegerValue());
    }
}
