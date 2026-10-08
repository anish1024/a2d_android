package com.a2d.app.domain.contract

import com.a2d.app.domain.model.ActivityAchieved

interface ActivityProcessor {
    val activities: List<ActivityAchieved>
    fun processActivity(callback: ((List<ActivityAchieved>) -> Boolean)? = null): Boolean
}
