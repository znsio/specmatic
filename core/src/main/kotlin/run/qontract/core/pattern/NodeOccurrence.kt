package run.qontract.core.pattern

import run.qontract.core.Result

enum class NodeOccurrence {
    Multiple {
        override fun encompasses(otherTypeOccurrence: NodeOccurrence): Result {
            return when(otherTypeOccurrence) {
                Optional, Multiple -> Result.Success()
                else -> Result.Failure("This node $description whereas the other ${otherTypeOccurrence.description}.")
            }
        }

        override val description: String = "may occur 0 or more times"
    },
    Optional {
        override fun encompasses(otherTypeOccurrence: NodeOccurrence): Result {
            return when(otherTypeOccurrence) {
                Once, Optional -> Result.Success()
                else -> Result.Failure("This node $description whereas the other ${otherTypeOccurrence.description}.")
            }
        }

        override val description: String = "is optional"
    },
    Once {
        override fun encompasses(otherTypeOccurrence: NodeOccurrence): Result {
            return when(otherTypeOccurrence) {
                Once -> Result.Success()
                else -> Result.Failure("This node $description whereas the other ${otherTypeOccurrence.description}.")
            }
        }

        override val description: String = "must occur"
    };

    abstract fun encompasses(otherTypeOccurrence: NodeOccurrence): Result
    abstract val description: String
}