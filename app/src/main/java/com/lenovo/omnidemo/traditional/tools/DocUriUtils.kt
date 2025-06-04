package com.lenovo.omnidemo.traditional.tools


/**
 * @date 2024/8/27 9:13
 * @author zhk
 */
var uriList = mutableListOf<String>()
fun setUri(docUriStr: String) {
    uriList.add(docUriStr)
}

fun getUri(): MutableList<String> {
    return uriList
}