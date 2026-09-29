# XNote currently relies on library-provided consumer rules.
# PDFBox's optional JPEG 2000 image decoder is not used by text extraction.
-dontwarn com.gemalto.jp2.JP2Decoder
# ONNX Runtime JNI resolves these Java classes and methods by name.
-keep class ai.onnxruntime.** { *; }
