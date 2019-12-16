/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.ort.reporter.reporters.webapp

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.ScannerDetails
import com.here.ort.model.Severity
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.ErrorResolutionReason
import com.here.ort.model.config.Resolutions
import com.here.ort.model.jsonMapper
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.spdx.SpdxExpression
import com.here.ort.utils.DeclaredLicenseProcessor
import com.here.ort.utils.ProcessedDeclaredLicense
import com.here.ort.utils.expandTilde
import java.io.File

import java.time.Instant
import java.util.SortedSet

fun main() {
    val ortResult = File("~/evaluation-result.json").expandTilde().readValue<OrtResult>()
    val resolutionProvider = DefaultResolutionProvider()
    resolutionProvider.add(ortResult.getResolutions())
    File("~/resolutions.yml").expandTilde().readValue<Resolutions>()
        .let { resolutionProvider.add(it) }

    val webAppModel = create(ortResult, resolutionProvider)

    val json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(webAppModel)
    println(json)
}

fun create(ortResult: OrtResult, resolutionProvider: ResolutionProvider): WebAppModel {
    val packages = mutableListOf<WebAppPackage>()
    val issues = mutableListOf<WebAppOrtIssue>()
    val resolutions = mutableListOf<WebAppResolution>()

    ortResult.analyzer?.result?.packages?.forEach { curatedPkg ->
        val pkg = curatedPkg.pkg

        val scanResults = ortResult.getScanResultsForId(pkg.id).map { result ->
            val startIndex = issues.size
            val endIndex = issues.size + result.summary.errors.size - 1
            val errorIndices = if (endIndex < startIndex) emptyList() else (startIndex..endIndex).toList()

            result.summary.errors.forEach { error ->
                val res = resolutionProvider.getErrorResolutionsFor(error)
                // TODO Add resolutions to global resolutions list and add indices below.

                val issue = WebAppOrtIssue(
                    timestamp = error.timestamp,
                    source = error.source,
                    message = error.message,
                    severity = error.severity,
                    resolutions = emptyList()
                )

                issues += issue
            }

            WebAppScanResult(
                provenance = result.provenance,
                scanner = result.scanner,
                startTime = result.summary.startTime,
                endTime = result.summary.endTime,
                fileCount = result.summary.fileCount,
                packageVerificationCode = result.summary.packageVerificationCode,
                errors = errorIndices
            )
        }

        val webAppPackage = WebAppPackage(
            id = pkg.id,
            purl = pkg.purl,
            declaredLicenses = pkg.declaredLicenses,
            declaredLicensesProcessed = pkg.declaredLicensesProcessed,
            concludedLicense = pkg.concludedLicense,
            description = pkg.description,
            homepageUrl = pkg.homepageUrl,
            binaryArtifact = pkg.binaryArtifact,
            sourceArtifact = pkg.sourceArtifact,
            vcs = pkg.vcs,
            vcsProcessed = pkg.vcsProcessed,
            scanResults = scanResults
        )

        packages += webAppPackage
    }

    return WebAppModel(
        packages = packages,
        issues = issues,
        resolutions = resolutions
    )
}

data class WebAppModel(
    val packages: List<WebAppPackage>,
    val issues: List<WebAppOrtIssue>,
    val resolutions: List<WebAppResolution>
)

data class WebAppPackage(
    val id: Identifier,
    val purl: String = id.toPurl(),
    val declaredLicenses: SortedSet<String>,
    val declaredLicensesProcessed: ProcessedDeclaredLicense = DeclaredLicenseProcessor.process(declaredLicenses),
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val concludedLicense: SpdxExpression? = null,
    val description: String,
    val homepageUrl: String,
    val binaryArtifact: RemoteArtifact,
    val sourceArtifact: RemoteArtifact,
    val vcs: VcsInfo,
    val vcsProcessed: VcsInfo = vcs.normalize(),

    //val curations: TODO

    val scanResults: List<WebAppScanResult>
)

data class WebAppScanResult(
    val provenance: Provenance,
    val scanner: ScannerDetails,
    val startTime: Instant,
    val endTime: Instant,
    val fileCount: Int,
    val packageVerificationCode: String,
//    val licenseFindings: SortedSet<LicenseFinding>,
//    val copyrightFindings: SortedSet<CopyrightFinding>,
    val errors: List<Int>
)

data class WebAppOrtIssue(
    val timestamp: Instant = Instant.now(),
    val source: String,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val resolutions: List<Int>
)

data class WebAppResolution(
    val message: String,
    val reason: ErrorResolutionReason,
    val comment: String
)
