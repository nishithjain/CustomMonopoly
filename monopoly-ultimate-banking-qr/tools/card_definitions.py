"""Canonical card definitions for Monopoly Ultimate Banking master registry."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class CardSpec:
    card_id: str
    card_type: str
    sequence: int
    name: str
    qr_filename: str
    front_filename: str


USER_CARDS: list[CardSpec] = [
    CardSpec("USR_01", "USER", 1, "Car", "01_Car_Back_QR.png", "Car_Front.jpg"),
    CardSpec("USR_02", "USER", 2, "Helicopter", "02_Helicopter_Back_QR.png", "Helicopter_Front.jpg"),
    CardSpec("USR_03", "USER", 3, "Ship", "03_Ship_Back_QR.png", "Ship_Front.jpg"),
    CardSpec("USR_04", "USER", 4, "Aeroplane", "04_Aeroplane_Back_QR.png", "Aeroplane_Front.jpg"),
]

EVENT_CARDS: list[CardSpec] = [
    CardSpec("EVT_01", "EVENT", 1, "Boom Town", "E01_Boom_Town_Back_QR.png", "Boom_Town_Front.png"),
    CardSpec("EVT_02", "EVENT", 2, "Crime Down", "E02_Crime_Down_Back_QR.png", "Crime_Down_Front.png"),
    CardSpec("EVT_03", "EVENT", 3, "Deal Of The Week", "E03_Deal_Of_The_Week_Back_QR.png", "Deal_Of_The_Week_Front.png"),
    CardSpec("EVT_04", "EVENT", 4, "Demolished", "E04_Demolished_Back_QR.png", "Demolished_Front.png"),
    CardSpec("EVT_05", "EVENT", 5, "Grand Designs", "E05_Grand_Designs_Back_QR.png", "Grand_Designs_Front.png"),
    CardSpec("EVT_06", "EVENT", 6, "Haunted House", "E06_Haunted_House_Back_QR.png", "Haunted_House_Front.png"),
    CardSpec("EVT_07", "EVENT", 7, "Highway Tax", "E07_Highway_Tax_Back_QR.png", "Highway_Tax_Front.png"),
    CardSpec("EVT_08", "EVENT", 8, "House Party", "E08_House_Party_Back_QR.png", "House_Party_Front.png"),
    CardSpec("EVT_09", "EVENT", 9, "In The Money", "E09_In_The_Money_Back_QR.png", "In_The_Money_Front.png"),
    CardSpec("EVT_10", "EVENT", 10, "It's A Boy!", "E10_Its_A_Boy_Back_QR.png", "Its_A_Boy_Front.png"),
    CardSpec("EVT_11", "EVENT", 11, "Love Is In The Air", "E11_Love_Is_In_The_Air_Back_QR.png", "Love_Is_In_The_Air_Front.png"),
    CardSpec("EVT_12", "EVENT", 12, "On The Map", "E12_On_The_Map_Back_QR.png", "On_The_Map_Front.png"),
    CardSpec("EVT_13", "EVENT", 13, "On The Run", "E13_On_The_Run_Back_QR.png", "On_The_Run_Front.png"),
    CardSpec("EVT_14", "EVENT", 14, "Pick Your Own", "E14_Pick_Your_Own_Back_QR.png", "Pick_Your_Own_Front.png"),
    CardSpec("EVT_15", "EVENT", 15, "Pong! What A Stinker", "E15_Pong_What_A_Stinker_Back_QR.png", "Pong_What_A_Stinker_Front.png"),
    CardSpec("EVT_16", "EVENT", 16, "Rover's Revenge", "E16_Rovers_Revenge_Back_QR.png", "Rovers_Revenge_Front.png"),
    CardSpec("EVT_17", "EVENT", 17, "Stargazing", "E17_Stargazing_Back_QR.png", "Stargazing_Front.png"),
    CardSpec("EVT_18", "EVENT", 18, "Stop The Presses", "E18_Stop_The_Presses_Back_QR.png", "Stop_The_Presses_Front.png"),
    CardSpec("EVT_19", "EVENT", 19, "'Tis The Season", "E19_Tis_The_Season_Back_QR.png", "Tis_The_Season_Front.png"),
    CardSpec("EVT_20", "EVENT", 20, "Tornado Alley", "E20_Tornado_Alley_Back_QR.png", "Tornado_Alley_Front.png"),
    CardSpec("EVT_21", "EVENT", 21, "Total Gridlock", "E21_Total_Gridlock_Back_QR.png", "Total_Gridlock_Front.png"),
    CardSpec("EVT_22", "EVENT", 22, "What A Ride!", "E22_What_A_Ride_Back_QR.png", "What_A_Ride_Front.png"),
    CardSpec("EVT_23", "EVENT", 23, "Wibble Wobble", "E23_Wibble_Wobble_Back_QR.png", "Wibble_Wobble_Front.png"),
]

PROPERTY_CARDS: list[CardSpec] = [
    CardSpec("PRP_01", "PROPERTY", 1, "Old Kent Road", "01_Old_Kent_Road_Back_QR.png", "01_Old_Kent_Road_Front.png"),
    CardSpec("PRP_02", "PROPERTY", 2, "Whitechapel Road", "02_Whitechapel_Road_Back_QR.png", "02_Whitechapel_Road_Front.png"),
    CardSpec("PRP_03", "PROPERTY", 3, "The Angel, Islington", "03_The_Angel_Islington_Back_QR.png", "03_The_Angel_Islington_Front.png"),
    CardSpec("PRP_04", "PROPERTY", 4, "Euston Road", "04_Euston_Road_Back_QR.png", "04_Euston_Road_Front.png"),
    CardSpec("PRP_05", "PROPERTY", 5, "Pentonville Road", "05_Pentonville_Road_Back_QR.png", "05_Pentonville_Road_Front.png"),
    CardSpec("PRP_06", "PROPERTY", 6, "Pall Mall", "06_Pall_Mall_Back_QR.png", "06_Pall_Mall_Front.png"),
    CardSpec("PRP_07", "PROPERTY", 7, "Whitehall", "07_Whitehall_Back_QR.png", "07_Whitehall_Front.png"),
    CardSpec("PRP_08", "PROPERTY", 8, "Northumberland Avenue", "08_Northumberland_Avenue_Back_QR.png", "08_Northumberland_Avenue_Front.png"),
    CardSpec("PRP_09", "PROPERTY", 9, "Bow Street", "09_Bow_Street_Back_QR.png", "09_Bow_Street_Front.png"),
    CardSpec("PRP_10", "PROPERTY", 10, "Marlborough Street", "10_Marlborough_Street_Back_QR.png", "10_Marlborough_Street_Front.png"),
    CardSpec("PRP_11", "PROPERTY", 11, "Vine Street", "11_Vine_Street_Back_QR.png", "11_Vine_Street_Front.png"),
    CardSpec("PRP_12", "PROPERTY", 12, "Strand", "12_Strand_Back_QR.png", "12_Strand_Front.png"),
    CardSpec("PRP_13", "PROPERTY", 13, "Fleet Street", "13_Fleet_Street_Back_QR.png", "13_Fleet_Street_Front.png"),
    CardSpec("PRP_14", "PROPERTY", 14, "Trafalgar Square", "14_Trafalgar_Square_Back_QR.png", "14_Trafalgar_Square_Front.png"),
    CardSpec("PRP_15", "PROPERTY", 15, "Leicester Square", "15_Leicester_Square_Back_QR.png", "15_Leicester_Square_Front.png"),
    CardSpec("PRP_16", "PROPERTY", 16, "Coventry Street", "16_Coventry_Street_Back_QR.png", "16_Coventry_Street_Front.png"),
    CardSpec("PRP_17", "PROPERTY", 17, "Piccadilly", "17_Piccadilly_Back_QR.png", "17_Piccadilly_Front.png"),
    CardSpec("PRP_18", "PROPERTY", 18, "Regent Street", "18_Regent_Street_Back_QR.png", "18_Regent_Street_Front.png"),
    CardSpec("PRP_19", "PROPERTY", 19, "Oxford Street", "19_Oxford_Street_Back_QR.png", "19_Oxford_Street_Front.png"),
    CardSpec("PRP_20", "PROPERTY", 20, "Bond Street", "20_Bond_Street_Back_QR.png", "20_Bond_Street_Front.png"),
    CardSpec("PRP_21", "PROPERTY", 21, "Park Lane", "21_Park_Lane_Back_QR.png", "21_Park_Lane_Front.png"),
    CardSpec("PRP_22", "PROPERTY", 22, "Mayfair", "22_Mayfair_Back_QR.png", "22_Mayfair_Front.png"),
]

CATEGORY_DIRS = {
    "USER": "Cards/UserCards",
    "EVENT": "Cards/EventCards",
    "PROPERTY": "Cards/PropertyCards",
}

ALL_CARDS = USER_CARDS + EVENT_CARDS + PROPERTY_CARDS
