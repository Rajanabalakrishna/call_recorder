// File: lib/services/native_recorder_bridge.dart
import 'package:flutter/services.dart';
import 'dart:developer' as developer;

/// Bridge to communicate with native Kotlin Accessibility Service
class NativeRecorderBridge {
  // FIXED: Match the package name in MainActivity.kt
  static const MethodChannel _channel = MethodChannel('com.example.recorder/native');

  /// Start recording to specified file path
  /// Returns true if recording started successfully
  static Future<bool> startRecording(String filePath) async {
    try {
      final result = await _channel.invokeMethod<bool>('startRecording', {
        'filePath': filePath,
      });
      developer.log('Native recording started: $result', name: 'RecorderBridge');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error starting recording: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Stop current recording
  /// Returns the file path where recording was saved, or null if failed
  static Future<String?> stopRecording() async {
    try {
      final result = await _channel.invokeMethod<String>('stopRecording');
      developer.log('Native recording stopped: $result', name: 'RecorderBridge');
      return result;
    } on PlatformException catch (e) {
      developer.log('Error stopping recording: ${e.message}', name: 'RecorderBridge', error: e);
      return null;
    }
  }

  /// Check if currently recording
  static Future<bool> isRecording() async {
    try {
      final result = await _channel.invokeMethod<bool>('isRecording');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error checking recording status: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Open device Accessibility Settings
  static Future<void> openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      developer.log('Error opening settings: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }

  /// Check if Accessibility Service is enabled
  static Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final result = await _channel.invokeMethod<bool>('isAccessibilityServiceEnabled');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error checking service: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Start foreground service (keeps app alive during recording)
  static Future<void> startForegroundService() async {
    try {
      await _channel.invokeMethod('startForegroundService');
      developer.log('Foreground service started', name: 'RecorderBridge');
    } on PlatformException catch (e) {
      developer.log('Error starting foreground service: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }

  /// Stop foreground service
  static Future<void> stopForegroundService() async {
    try {
      await _channel.invokeMethod('stopForegroundService');
      developer.log('Foreground service stopped', name: 'RecorderBridge');
    } on PlatformException catch (e) {
      developer.log('Error stopping foreground service: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }
}