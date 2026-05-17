package eu.kanade.tachiyomi.animesource.model

sealed class AnimeFilter<T>(val name: String, var state: T) {
    class Text(name: String, state: String = "") : AnimeFilter<String>(name, state)
    class CheckBox(name: String, state: Boolean = false) : AnimeFilter<Boolean>(name, state)
    class TriState(name: String, state: TriStateGroup = TriStateGroup()) : AnimeFilter<TriStateGroup>(name, state)
    class Sort(name: String, state: SelectionList = SelectionList()) : AnimeFilter<SelectionList>(name, state)
    class Select<T>(name: String, val values: List<T>, state: Int = 0) : AnimeFilter<Int>(name, state)
    class Header(name: String) : AnimeFilter<String>(name, "")
    class Separator : AnimeFilter<String>("", "")
    class Group<T>(name: String, state: List<T> = emptyList()) : AnimeFilter<List<T>>(name, state)
}

data class TriStateGroup(val state: Int = 0)
data class SelectionList(val selection: List<Int> = emptyList(), val index: Int = 0)

typealias AnimeFilterList = List<AnimeFilter<*>>
