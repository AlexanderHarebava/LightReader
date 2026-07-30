plugins {
    id("readium.library-conventions")
}

android {
    namespace = "org.readium.adapter.pdfium.document"


    packaging {
        resources {
            pickFirsts += setOf(
                "com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
                "com/tom_roush/pdfbox/resources/glyphlist/zapfdingbats.txt",

                "com/tom_roush/pdfbox/resources/text/BidiMirroring.txt",
                "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf",
                "com/tom_roush/fontbox/resources/cmap/Identity-H",
                "com/tom_roush/fontbox/resources/cmap/Identity-V",
                "com/tom_roush/fontbox/resources/cmap/H",
                "com/tom_roush/fontbox/resources/cmap/V",
                "com/tom_roush/fontbox/resources/cmap/78",
                "com/tom_roush/fontbox/resources/cmap/78ms",
                "com/tom_roush/fontbox/resources/cmap/78rksj",
                "com/tom_roush/fontbox/resources/cmap/78v",
                "com/tom_roush/fontbox/resources/cmap/83pv-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/90ms-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/90ms-RKSJ-V",
                "com/tom_roush/fontbox/resources/cmap/90msp-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/90msp-RKSJ-V",
                "com/tom_roush/fontbox/resources/cmap/90pv-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/Add-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/Add-RKSJ-V",
                "com/tom_roush/fontbox/resources/cmap/EUC-H",
                "com/tom_roush/fontbox/resources/cmap/EUC-V",
                "com/tom_roush/fontbox/resources/cmap/Ext-RKSJ-H",
                "com/tom_roush/fontbox/resources/cmap/Ext-RKSJ-V",
                "com/tom_roush/fontbox/resources/cmap/GB-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/GB-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/GBpc-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/GBpc-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/GBT-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/GBT-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/GBTpc-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/GBTpc-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/GBx-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/GBx-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/HKdla-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKdla-B5-V",
                "com/tom_roush/fontbox/resources/cmap/HKdlb-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKdlb-B5-V",
                "com/tom_roush/fontbox/resources/cmap/HKgccs-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKgccs-B5-V",
                "com/tom_roush/fontbox/resources/cmap/HKm314-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKm314-B5-V",
                "com/tom_roush/fontbox/resources/cmap/HKm471-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKm471-B5-V",
                "com/tom_roush/fontbox/resources/cmap/HKscs-B5-H",
                "com/tom_roush/fontbox/resources/cmap/HKscs-B5-V",
                "com/tom_roush/fontbox/resources/cmap/KSC-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/KSC-EUC-V",
                "com/tom_roush/fontbox/resources/cmap/KSCms-UHC-H",
                "com/tom_roush/fontbox/resources/cmap/KSCms-UHC-V",
                "com/tom_roush/fontbox/resources/cmap/KSCpc-EUC-H",
                "com/tom_roush/fontbox/resources/cmap/UniCNS-UTF16-H",
                "com/tom_roush/fontbox/resources/cmap/UniCNS-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniCNS-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniCNS-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/UniGB-UTF16-H",
                "com/tom_roush/fontbox/resources/cmap/UniGB-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniGB-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniGB-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/UniJIS-UTF16-H",
                "com/tom_roush/fontbox/resources/cmap/UniJIS-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniJIS-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniJIS-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/UniJIS2004-UTF16-H",
                "com/tom_roush/fontbox/resources/cmap/UniJIS2004-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniJIS2004-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniJIS2004-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/UniJISPro-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniJISPro-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/UniJISPro-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniJISX0213-UTF32-H",
                "com/tom_roush/fontbox/resources/cmap/UniJISX0213-UTF32-V",
                "com/tom_roush/fontbox/resources/cmap/UniJISX02132004-UTF32-H",
                "com/tom_roush/fontbox/resources/cmap/UniJISX02132004-UTF32-V",
                "com/tom_roush/fontbox/resources/cmap/UniKS-UTF16-H",
                "com/tom_roush/fontbox/resources/cmap/UniKS-UTF16-V",
                "com/tom_roush/fontbox/resources/cmap/UniKS-UTF8-H",
                "com/tom_roush/fontbox/resources/cmap/UniKS-UTF8-V",
                "com/tom_roush/fontbox/resources/cmap/wp-Symbol"
            )

            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    api(project(":readium:readium-shared"))
    implementation(libs.pdfviewer)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)
}