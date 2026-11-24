package com.lumera.app.data.model.stremio

/** Entry in Stremio's official add-on index. */
data class OfficialAddonDescriptor(
    val manifest: Manifest = Manifest(),
    val transportUrl: String = ""
)
