package kellerar.triliumdroid

import java.util.SortedMap

public class Branch(
        val id: String,
        val note: String,
        val parentNote: String?,
        val position: Int,
        val prefix: String?,
        val expanded: Boolean,
        public var children: SortedMap<Int, Branch>
) {

}
