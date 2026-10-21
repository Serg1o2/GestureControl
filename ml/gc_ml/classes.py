"""Unified class set and dataset-to-unified mappings (documented in docs/class_mapping.md)."""

from __future__ import annotations

# Static hand poses predicted by the pose model. Order = model output index; never reorder,
# append only (the app reads the order from the model metadata).
POSE_CLASSES = [
    "OTHER",        # hand present, none of the poses below (includes HaGRID no_gesture)
    "OPEN_PALM",
    "FIST",
    "POINTING",     # index extended, others folded
    "TWO_FINGERS",  # index + middle extended
    "THUMB_UP",
    "THUMB_DOWN",
]
POSE_INDEX = {name: i for i, name in enumerate(POSE_CLASSES)}

# HaGRIDv2 label -> unified pose. Everything not listed maps to OTHER.
HAGRID_TO_POSE = {
    "palm": "OPEN_PALM",
    "stop": "OPEN_PALM",
    "stop_inverted": "OPEN_PALM",
    "fist": "FIST",
    "one": "POINTING",
    "point": "POINTING",
    "peace": "TWO_FINGERS",
    "peace_inverted": "TWO_FINGERS",
    "two_up": "TWO_FINGERS",
    "two_up_inverted": "TWO_FINGERS",
    "like": "THUMB_UP",
    "dislike": "THUMB_DOWN",
}

HAGRID_ALL = [
    "call", "peace", "grabbing", "dislike", "peace_inverted", "grip", "fist", "rock", "hand_heart",
    "four", "stop", "hand_heart2", "like", "stop_inverted", "holy", "mute", "three", "little_finger",
    "ok", "three2", "middle_finger", "one", "two_up", "point", "palm", "two_up_inverted", "take_picture",
    "three3", "three_gun", "thumb_index", "thumb_index2", "timeout", "xsign", "no_gesture",
]


def hagrid_pose(label: str) -> str:
    return HAGRID_TO_POSE.get(label, "OTHER")


# MediaPipe Gesture Recognizer (baseline) -> unified pose, for comparison on shared classes.
MP_RECOGNIZER_TO_POSE = {
    "None": "OTHER",
    "Closed_Fist": "FIST",
    "Open_Palm": "OPEN_PALM",
    "Pointing_Up": "POINTING",
    "Thumb_Down": "THUMB_DOWN",
    "Thumb_Up": "THUMB_UP",
    "Victory": "TWO_FINGERS",
    "ILoveYou": "OTHER",
}

# Dynamic gestures (sequence model). Order = model output index.
DYNAMIC_CLASSES = [
    "NO_GESTURE",
    "SWIPE_LEFT",    # whole hand moves to the user's left
    "SWIPE_RIGHT",
    "SWIPE_UP",
    "SWIPE_DOWN",
    "FIST_OPEN",     # closed hand opens
]

# IPN Hand label -> dynamic class. Directions are from the user's point of view (see docs).
IPN_TO_DYNAMIC = {
    "D0X": "NO_GESTURE",
    "B0A": "NO_GESTURE",  # pointing with one finger (cursor-like motion must not trigger)
    "B0B": "NO_GESTURE",
    "G01": "NO_GESTURE",
    "G02": "NO_GESTURE",
    "G03": "SWIPE_UP",
    "G04": "SWIPE_DOWN",
    "G05": "SWIPE_LEFT",
    "G06": "SWIPE_RIGHT",
    "G07": "FIST_OPEN",   # "open twice": fist opens (twice)
    "G08": "NO_GESTURE",
    "G09": "NO_GESTURE",
    "G10": "NO_GESTURE",  # zoom in/out: not used by the app
    "G11": "NO_GESTURE",
}
