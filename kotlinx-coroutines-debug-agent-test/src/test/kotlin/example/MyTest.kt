package example

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.yield
import org.junit.Test

class MyTest : TestBase() {
    @Test
    fun testSomething() = runBlocking<Unit> {
        expect(1)
        val job = launch(coroutineContext + CoroutineName("job")) {
            expect(3)
        }
        expect(2)
        yield()
        finish(4)
    }
}