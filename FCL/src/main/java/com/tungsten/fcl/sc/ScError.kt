package com.tungsten.fcl.sc

/** SC: an error shown to the player in our page as a card (title + plain Spanish text). */
internal class ScError(val titulo: String, val texto: String) : Exception(texto)
