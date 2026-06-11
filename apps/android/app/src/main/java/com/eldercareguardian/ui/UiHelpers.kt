package com.eldercareguardian.ui

import com.eldercareguardian.data.EcgAnomalyStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.PostureStatus
import com.eldercareguardian.data.RiskStatus

fun riskProgress(status: RiskStatus): Float = when (status) {
    RiskStatus.Low -> 0.25f
    RiskStatus.Medium -> 0.6f
    RiskStatus.High -> 0.92f
}

fun postureProgress(status: PostureStatus): Float = when (status) {
    PostureStatus.Good -> 0.9f
    PostureStatus.Warning -> 0.58f
    PostureStatus.Bad -> 0.25f
}

fun fatigueProgress(status: FatigueStatus): Float = when (status) {
    FatigueStatus.Safe -> 0.25f
    FatigueStatus.Caution -> 0.65f
    FatigueStatus.Stop -> 0.95f
}

fun rhythmSeverity(status: EcgAnomalyStatus): RiskStatus = when (status) {
    EcgAnomalyStatus.Normal, EcgAnomalyStatus.Unknown -> RiskStatus.Low
    EcgAnomalyStatus.AFib -> RiskStatus.High
    EcgAnomalyStatus.Tachycardia, EcgAnomalyStatus.Bradycardia -> RiskStatus.Medium
}

fun rhythmDescription(status: EcgAnomalyStatus): String = when (status) {
    EcgAnomalyStatus.Unknown -> "Not enough RR intervals yet \u2014 algorithm needs at least 4 beats."
    EcgAnomalyStatus.Normal -> "Rhythm looks regular, RMSSD in expected range."
    EcgAnomalyStatus.AFib -> "Irregular RR intervals with high variability \u2014 flag for caregiver review."
    EcgAnomalyStatus.Tachycardia -> "Sustained elevated heart rate (\u2265 100 bpm) \u2014 flag for review."
    EcgAnomalyStatus.Bradycardia -> "Sustained low heart rate (\u2264 50 bpm) \u2014 flag for review."
}
