package com.takibo.audit.annotations;

public enum MaskMode {
    FULL,           // "********"
    LEFT,           // garde showRight
    RIGHT,          // garde showLeft
    CENTER,         // garde showLeft + showRight
    FROM_LEFT,      // garde les fromLeft premiers
    FROM_RIGHT,     // garde les fromRight derniers
    RATIO,          // masque un pourcentage au centre
    SYMBOLIC,       // plein de symbol()
    WORD,           // remplace un mot donné (case-insensitive)
    INDEX           // masque un/plusieurs index précis
}
