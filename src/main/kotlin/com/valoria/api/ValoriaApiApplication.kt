package com.valoria.api

import com.valoria.api.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
class ValoriaApiApplication

fun main(args: Array<String>) {
    runApplication<ValoriaApiApplication>(*args)
}

