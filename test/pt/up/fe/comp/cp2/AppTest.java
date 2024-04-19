package pt.up.fe.comp.cp2;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class AppTest {

    @Test
    public void testHelloWorld() {
        var code = SpecsIo.getResource("pt/up/fe/comp/cp2/apps/HelloWorld.jmm");
        var ollirResult = TestUtils.optimize(code, Collections.emptyMap());
        System.out.println(ollirResult.getOllirCode());
        var jasminResult = TestUtils.backend(code, Collections.emptyMap());
        System.out.println(jasminResult.getJasminCode());
        var result = TestUtils.runJasmin(jasminResult.getJasminCode(), Collections.emptyMap());
        assertEquals("Hello, World!", result.strip());
    }

    @Test
    public void testSimple() {
        var code = SpecsIo.getResource("pt/up/fe/comp/cp2/apps/Simple.jmm");
        var ollirResult = TestUtils.optimize(code, Collections.emptyMap());
        System.out.println(ollirResult.getOllirCode());
        var jasminResult = TestUtils.backend(code, Collections.emptyMap());
        System.out.println(jasminResult.getJasminCode());
        var result = TestUtils.runJasmin(jasminResult.getJasminCode(), Collections.emptyMap());
        assertEquals("30", result.strip());
    }

    @Test
    public void testCustom() {
        var code = SpecsIo.getResource("pt/up/fe/comp/cp2/ollir/CompileArithmetic.jmm");
        var ollirResult = TestUtils.optimize(code, Collections.emptyMap());
        System.out.println(ollirResult.getOllirCode());
        var jasminResult = TestUtils.backend(code, Collections.emptyMap());
        System.out.println(jasminResult.getJasminCode());
        var result = TestUtils.runJasmin(jasminResult.getJasminCode(), Collections.emptyMap());
        assertEquals("10", result.strip());
    }

}
