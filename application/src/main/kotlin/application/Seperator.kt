package application;

import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Command(
    name = " ",
    description = [" "]
)
class Separator : Callable<Int> {
    override fun call(): Int = 0
}