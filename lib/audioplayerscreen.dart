// File: lib/screens/audio_player_screen.dart
import 'package:flutter/material.dart';
import 'package:audioplayers/audioplayers.dart';
import 'dart:io';

class AudioPlayerScreen extends StatefulWidget {
  final String filePath;
  final String fileName;

  const AudioPlayerScreen({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  @override
  State<AudioPlayerScreen> createState() => _AudioPlayerScreenState();
}

class _AudioPlayerScreenState extends State<AudioPlayerScreen> {
  late AudioPlayer _audioPlayer;
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;
  double _playbackSpeed = 1.0;

  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }

  Future<void> _initializePlayer() async {
    _audioPlayer = AudioPlayer();

    // Listen to player state changes
    _audioPlayer.onPlayerStateChanged.listen((state) {
      if (mounted) {
        setState(() {
          _isPlaying = state == PlayerState.playing;
        });
      }
    });

    // Listen to duration changes
    _audioPlayer.onDurationChanged.listen((duration) {
      if (mounted) {
        setState(() {
          _duration = duration;
        });
      }
    });

    // Listen to position changes
    _audioPlayer.onPositionChanged.listen((position) {
      if (mounted) {
        setState(() {
          _position = position;
        });
      }
    });

    // Auto-complete handler
    _audioPlayer.onPlayerComplete.listen((event) {
      if (mounted) {
        setState(() {
          _position = Duration.zero;
          _isPlaying = false;
        });
      }
    });

    // Load the audio file
    await _audioPlayer.setSourceDeviceFile(widget.filePath);
  }

  Future<void> _togglePlayPause() async {
    if (_isPlaying) {
      await _audioPlayer.pause();
    } else {
      await _audioPlayer.resume();
    }
  }

  Future<void> _seekTo(Duration position) async {
    await _audioPlayer.seek(position);
  }

  Future<void> _skipForward() async {
    final newPosition = _position + const Duration(seconds: 10);
    await _seekTo(newPosition > _duration ? _duration : newPosition);
  }

  Future<void> _skipBackward() async {
    final newPosition = _position - const Duration(seconds: 10);
    await _seekTo(newPosition < Duration.zero ? Duration.zero : newPosition);
  }

  Future<void> _changeSpeed(double speed) async {
    await _audioPlayer.setPlaybackRate(speed);
    setState(() {
      _playbackSpeed = speed;
    });
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '$hours:${twoDigits(minutes)}:${twoDigits(seconds)}';
    }
    return '${twoDigits(minutes)}:${twoDigits(seconds)}';
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Play Recording'),
        actions: [
          // Speed control menu
          PopupMenuButton<double>(
            icon: const Icon(Icons.speed),
            onSelected: _changeSpeed,
            itemBuilder: (context) => [
              PopupMenuItem(value: 0.5, child: Text('0.5x ${_playbackSpeed == 0.5 ? '✓' : ''}')),
              PopupMenuItem(value: 0.75, child: Text('0.75x ${_playbackSpeed == 0.75 ? '✓' : ''}')),
              PopupMenuItem(value: 1.0, child: Text('1.0x ${_playbackSpeed == 1.0 ? '✓' : ''}')),
              PopupMenuItem(value: 1.25, child: Text('1.25x ${_playbackSpeed == 1.25 ? '✓' : ''}')),
              PopupMenuItem(value: 1.5, child: Text('1.5x ${_playbackSpeed == 1.5 ? '✓' : ''}')),
              PopupMenuItem(value: 2.0, child: Text('2.0x ${_playbackSpeed == 2.0 ? '✓' : ''}')),
            ],
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Album art / Icon
            Container(
              width: 200,
              height: 200,
              decoration: BoxDecoration(
                color: Colors.blue.shade100,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Icon(
                Icons.phone_in_talk,
                size: 100,
                color: Colors.blue.shade700,
              ),
            ),

            const SizedBox(height: 40),

            // File name
            Text(
              widget.fileName,
              style: Theme.of(context).textTheme.titleLarge,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),

            const SizedBox(height: 10),

            // Playback speed indicator
            Text(
              'Speed: ${_playbackSpeed}x',
              style: TextStyle(
                color: Colors.grey[600],
                fontSize: 14,
              ),
            ),

            const SizedBox(height: 30),

            // Progress bar
            Column(
              children: [
                Slider(
                  min: 0,
                  max: _duration.inSeconds.toDouble(),
                  value: _position.inSeconds.toDouble(),
                  onChanged: (value) async {
                    final newPosition = Duration(seconds: value.toInt());
                    await _seekTo(newPosition);
                  },
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        _formatDuration(_position),
                        style: const TextStyle(fontSize: 12),
                      ),
                      Text(
                        _formatDuration(_duration),
                        style: const TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: 30),

            // Control buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Skip backward 10s
                IconButton(
                  iconSize: 40,
                  icon: const Icon(Icons.replay_10),
                  onPressed: _skipBackward,
                ),

                const SizedBox(width: 20),

                // Play/Pause button
                Container(
                  width: 70,
                  height: 70,
                  decoration: BoxDecoration(
                    color: Colors.blue,
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    iconSize: 40,
                    color: Colors.white,
                    icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                    onPressed: _togglePlayPause,
                  ),
                ),

                const SizedBox(width: 20),

                // Skip forward 10s
                IconButton(
                  iconSize: 40,
                  icon: const Icon(Icons.forward_10),
                  onPressed: _skipForward,
                ),
              ],
            ),

            const SizedBox(height: 40),

            // File info
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('File Size:'),
                      FutureBuilder<int>(
                        future: File(widget.filePath).length(),
                        builder: (context, snapshot) {
                          if (snapshot.hasData) {
                            final bytes = snapshot.data!;
                            final mb = (bytes / (1024 * 1024)).toStringAsFixed(2);
                            return Text('$mb MB');
                          }
                          return const Text('...');
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Duration:'),
                      Text(_formatDuration(_duration)),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}