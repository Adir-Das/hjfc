package com.example.core.config

import com.example.BuildConfig
import java.time.ZoneId

object AppConfig {
    val DHAKA_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("your-project-ref") &&
            supabaseAnonKey.isNotBlank() &&
            supabaseAnonKey != "YOUR_SUPABASE_ANON_KEY"
}
