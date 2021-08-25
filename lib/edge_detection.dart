import 'dart:async';

import 'package:flutter/services.dart';

class EdgeDetection {
  static const MethodChannel _channel = const MethodChannel('edge_detection');

  static Future<List<dynamic>?> get detectEdge async {
    final List<dynamic>? imagePath = await _channel.invokeMethod('edge_detect');
    return imagePath;
  }
}
