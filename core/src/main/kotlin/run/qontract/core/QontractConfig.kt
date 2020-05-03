package run.qontract.core

val qontractDirPath: String = "${System.getProperty("user.home")}/.qontract"
val qontractCacheDirPath = "$qontractDirPath/cache"
val qontractRepoDirPath = "$qontractDirPath/repos"

const val POINTER_EXTENSION = "pointer"
const val CONTRACT_EXTENSION = "qontract"
const val DATA_DIR_SUFFIX = "_data"