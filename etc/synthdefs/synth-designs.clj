;;--
;; This file is part of Sonic Pi: http://sonic-pi.net
;; Full project source: https://github.com/samaaron/sonic-pi
;; License: https://github.com/samaaron/sonic-pi/blob/master/LICENSE.md
;;
;; Copyright 2013, 2014 by Sam Aaron (http://sam.aaron.name).
;; All rights reserved.
;;
;; Permission is granted for use, copying, modification, distribution,
;; and distribution of modified versions of this work as long as this
;; notice is included.
;;++

;; This file uses Overtone to compile the synths from Clojure to
;; SuperCollider compatible binary files. Overtone is Sonic Pi's big
;; brother. See: http://overtone.github.io

(ns sp
  (:use [overtone.live])

  (:require [clojure.string :as str]
            [overtone.sc.dyn-vars :as dvars])
  )

;; Utility functions (for creating and storing synthdefs)

(defn save-synthdef [sdef folder]
  (let [path (str folder "/" (last (str/split (-> sdef :sdef :name) #"/")) ".scsyndef") ]
    (overtone.sc.machinery.synthdef/synthdef-write (:sdef sdef) path)
    path))

(defn save-to-pi [sdef]
  (save-synthdef sdef "/Users/sam/Development/RPi/sonic-pi/etc/synthdefs/"))

;; Main mixer

(do
  (defsynth mixer [in_bus 0 amp 1 safe-recovery-time 3]
    (let [source   (in in_bus 2)
          source   (* amp source)
          source   (lpf source 20000)
          amp      (lag-ud amp 0 0.02)
          safe-snd (limiter source 0.99 0.001)]
      (replace-out 0 safe-snd)))

  (defsynth basic_mixer [in_bus 0 out_bus 0 amp 1 amp_slide 0.5]
    (let [amp (lag amp amp_slide)
          src (in in_bus 2)
          src (* amp src)]
      (out out_bus src)))

  (defsynth recorder
    [out-buf 0 in_bus 0]
    (disk-out out-buf (in in_bus 2)))

  (comment
    (save-to-pi mixer)
    (save-to-pi basic_mixer)
    (save-to-pi recorder)))


;; Simple Trigger synths

(do
  (def dull-partials
    [
     0.56
     ;;   1.19
     ;;   1.71
     ;;   2.74
     3
     ;;   3.76
     ])

  ;; http://www.soundonsound.com/sos/Aug02/articles/synthsecrets0802.asp
  ;; (fig 8)
  (def partials
    [
     1
     4
     ;;   3
     ;;   4.2
     ;;   5.4
     ;;   6.8
     ])

  ;; we make a bell by combining a set of sine waves at the given
  ;; proportions of the frequency. Technically not really partials
  ;; as for the 'pretty bell' I stuck mainly with harmonics.
  ;; Each partial is mixed down proportional to its number - so 1 is
  ;; louder than 6. Higher partials are also supposed to attenuate
  ;; quicker but setting the release didn't appear to do much.

  (defcgen bell-partials
    "Bell partial generator"
    [freq     {:default 440 :doc "The fundamental frequency for the partials"}
     attack   {:default 0.01}
     sustain  {:default 0}
     release  {:default 1.0 :doc "Duration multiplier. Length of longest partial will
                            be dur seconds"}
     partials {:default [0.5 1 2 4] :doc "sequence of frequencies which are
                                        multiples of freq"}]
    "Generates a series of progressively shorter and quieter enveloped sine waves
  for each of the partials specified. The length of the envolope is proportional
  to dur and the fundamental frequency is specified with freq."
    (:ar
     (apply +
            (map
             (fn [partial proportion]
               (let [vol      (/ proportion 2)
                     env      (env-gen (envelope [0 1 1 0] [attack sustain (* release proportion)]) :level-scale vol)
                     overtone (* partial freq)]
                 (* env (sin-osc overtone))))
             partials               ;; current partial
             (iterate #(/ % 2) 1.0) ;; proportions (1.0  0.5 0.25)  etc
             ))))

  (without-namespace-in-synthdef
   (defsynth dull_bell [note 52
                        note_slide 0
                        amp 1
                        amp_slide 0
                        pan 0
                        pan_slide 0
                        attack 0.01
                        sustain 0
                        release 1.0
                        out_bus 0]
     (let [note (lag note note_slide)
           amp  (lag amp amp_slide)
           pan  (lag pan pan_slide)
           freq (midicps note)
           snd  (* amp (bell-partials freq attack sustain release dull-partials))]
       (detect-silence snd :action FREE)
       (out out_bus (pan2 snd pan))))

   (defsynth pretty_bell [note 52
                          note_slide 0
                          amp 1
                          amp_slide 0
                          pan 0
                          pan_slide 0
                          attack 0.01
                          sustain 0
                          release 1
                          out_bus 0]
     (let [note (lag note note_slide)
           amp  (lag amp amp_slide)
           pan  (lag pan pan_slide)
           freq (midicps note)
           snd  (* amp (bell-partials freq attack sustain release partials))]
       (detect-silence snd :action FREE)
       (out out_bus (pan2 snd pan))))

   (defsynth beep [note 52
                   note_slide 0
                   amp 1
                   amp_slide 0
                   pan 0
                   pan_slide 0
                   attack 0
                   sustain 0
                   release 0.2
                   out_bus 0]
     (let [note (lag note note_slide)
           amp  (lag amp amp_slide)
           pan  (lag pan pan_slide)
           freq (midicps note)]
       (out out_bus (pan2 (* (sin-osc freq)
                             (env-gen (envelope [0 1 1 0] [attack sustain release]) :level-scale amp :action FREE)
                             )
                          pan))))


   (defsynth saw_beep [note 52
                       note_slide 0
                       amp 1
                       amp_slide 0
                       pan 0
                       pan_slide 0
                       attack 0.1
                       sustain 0
                       release 0.3
                       cutoff 100
                       cutoff_slide 0
                       out_bus 0]
     (let [note        (lag note note_slide)
           amp         (lag amp amp_slide)
           pan         (lag pan pan_slide)
           cutoff      (lag cutoff cutoff_slide)
           freq        (midicps note)
           cutoff-freq (midicps cutoff)]
       (out out_bus (pan2 (* (normalizer (lpf (saw freq) cutoff-freq))
                             (env-gen (envelope [0 1 1 0] [attack sustain release]) :level-scale amp :action FREE))
                          pan))))

   (defsynth dsaw [note 52
                   note_slide 0
                   amp 1
                   amp_slide 0
                   pan 0
                   pan_slide 0
                   attack 0.1
                   sustain 0
                   release 0.3
                   cutoff 100
                   cutoff_slide 0
                   detune 0.1
                   detune_slide 0
                   out_bus 0]
     (let [note        (lag note note_slide)
           _    (poll (impulse 3) note "note")


           amp         (lag amp amp_slide)
           pan         (lag pan pan_slide)
           detune      (lag detune detune_slide)
           cutoff      (lag cutoff cutoff_slide)
           freq        (midicps note)
           cutoff-freq (midicps cutoff)
           detune-freq (midicps (+ note detune))]
       (out out_bus (pan2 (* (normalizer (lpf (mix (saw [freq detune-freq])) cutoff-freq))
                             (env-gen (envelope [0 1 1 0] [attack sustain release]) :level-scale amp :action FREE)
                             )
                          pan))))

   (defsynth fm [note 52
                 note_slide 0
                 amp 1
                 amp_slide 0
                 pan 0
                 pan_slide 0
                 attack 1
                 sustain 0
                 release 1
                 divisor 2.0
                 divisor_slide 0
                 depth 1.0
                 depth_slide 0
                 out_bus 0]
     (let [note      (lag note note_slide)
           amp       (lag amp amp_slide)
           pan       (lag pan pan_slide)
           divisor   (lag divisor divisor_slide)
           depth     (lag depth depth_slide)
           carrier   (midicps note)
           modulator (/ carrier divisor)
           env       (env-gen (env-lin attack sustain release) :level-scale amp :action FREE)]
       (out out_bus (pan2 (* env
                             (sin-osc (+ carrier
                                         (* env  (* carrier depth) (sin-osc modulator)))))
                          pan))))


   (defsynth mod_saw [note 52
                      note_slide 0
                      amp 1
                      amp_slide 0
                      pan 0
                      pan_slide 0
                      attack 0.01
                      sustain 0
                      release 2
                      cutoff 100
                      cutoff_slide 0
                      mod_rate 1
                      mod_rate_slide 0
                      mod_range 5
                      mod_range_slide 0
                      mod_width 0.5
                      mod_width_slide 0
                      out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           cutoff         (lag cutoff cutoff_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           freq           (midicps note)
           cutoff-freq    (midicps cutoff)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (saw freq)
           snd            (lpf snd cutoff-freq)
           snd            (normalizer snd)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_saw_s [note 52
                        note_slide 0
                        amp 1
                        amp_slide 0
                        pan 0
                        pan_slide 0
                        attack 0.01
                        sustain 0
                        release 2
                        mod_rate 1
                        mod_rate_slide 0
                        mod_range 5
                        mod_range_slide 0
                        mod_width 0.5
                        mod_width_slide 0
                        out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           freq           (midicps note)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (saw freq)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_dsaw [note 52
                       note_slide 0
                       amp 1
                       amp_slide 0
                       pan 0
                       pan_slide 0
                       attack 0.01
                       sustain 0
                       release 2
                       cutoff 100
                       cutoff_slide 0
                       mod_rate 1
                       mod_rate_slide 0
                       mod_range 5
                       mod_range_slide 0
                       mod_width 0.5
                       mod_width_slide 0
                       detune 0.1
                       detune_slide 0
                       out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           cutoff         (lag cutoff cutoff_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           detune         (lag detune detune_slide)
           freq           (midicps note)
           cutoff-freq    (midicps cutoff)
           mod-range-freq (- (midicps (+ mod_range note))
                             freq)
           detune-freq    (midicps (+ note detune))
           freq-mod       (* mod-range-freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (mix (saw [freq detune-freq]))
           snd            (lpf snd cutoff-freq)
           snd            (normalizer snd)
           env            (env-gen (env-lin attack release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_dsaw_s [note 52
                         note_slide 0
                         amp 1
                         amp_slide 0
                         pan 0
                         pan_slide 0
                         attack 0.01
                         sustain 0
                         release 2
                         mod_rate 1
                         mod_rate_slide 0
                         mod_range 5
                         mod_range_slide 0
                         mod_width 0.5
                         mod_width_slide 0
                         detune 0.1
                         detune_slide 0
                         out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           detune         (lag detune detune_slide)

           freq           (midicps note)
           mod-range-freq (- (midicps (+ mod_range note))
                             freq)
           detune-freq    (midicps (+ note detune))
           freq-mod       (* mod-range-freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (mix (saw [freq detune-freq]))
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))


   (defsynth mod_sine [note 52
                       note_slide 0
                       amp 1
                       amp_slide 0
                       pan 0
                       pan_slide 0
                       attack 0.01
                       sustain 0
                       release 2
                       cutoff 100
                       cutoff_slide 0
                       mod_rate 1
                       mod_rate_slide 0
                       mod_range 5
                       mod_range_slide 0
                       mod_width 0.5
                       mod_width_slide 0
                       out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           cutoff         (lag cutoff cutoff_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           freq           (midicps note)
           cutoff-freq    (midicps cutoff)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (sin-osc freq)
           snd            (lpf snd cutoff-freq)
           snd            (normalizer snd)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_sine_s [note 52
                         note_slide 0
                         amp 1
                         amp_slide 0
                         pan 0
                         pan_slide 0
                         attack 0.01
                         sustain 0
                         release 2
                         mod_rate 1
                         mod_rate_slide 0
                         mod_range 5
                         mod_range_slide 0
                         mod_width 0.5
                         mod_width_slide 0
                         out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           freq           (midicps note)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (sin-osc freq)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_tri [note 52
                      note_slide 0
                      amp 1
                      amp_slide 0
                      pan 0
                      pan_slide 0
                      attack 0.01
                      sustain 0
                      release 2
                      cutoff 100
                      cutoff_slide 0
                      mod_rate 1
                      mod_rate_slide 0
                      mod_range 5
                      mod_range_slide 0
                      mod_width 0.5
                      mod_width_slide 0
                      out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           cutoff         (lag cutoff cutoff_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           freq           (midicps note)
           cutoff-freq    (midicps cutoff)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (lf-tri freq)
           snd            (lpf snd cutoff-freq)
           snd            (normalizer snd)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_tri_s [note 52
                        note_slide 0
                        amp 1
                        amp_slide 0
                        pan 0
                        pan_slide 0
                        attack 0.01
                        sustain 0
                        release 2
                        mod_rate 1
                        mod_rate_slide 0
                        mod_range 5
                        mod_range_slide 0
                        mod_width 0.5
                        mod_width_slide 0
                        out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width_slide)
           freq           (midicps note)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (lf-tri freq)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_pulse [note 52
                        note_slide 0
                        amp 1
                        amp_slide 0
                        pan 0
                        pan_slide 0
                        attack 0.01
                        sustain 0
                        release 2
                        cutoff 100
                        cutoff_slide 0
                        mod_rate 1
                        mod_rate_slide 0
                        mod_range 5
                        mod_range_slide 0
                        mod_width 0.5
                        mod_width_slide 0
                        pulse_width 0.5
                        pulse_width_slide 0
                        out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           cutoff         (lag cutoff cutoff_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           pulse_width    (lag pulse_width pulse_width_slide)
           freq           (midicps note)
           cutoff-freq    (midicps cutoff)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (pulse freq pulse_width)
           snd            (lpf snd cutoff-freq)
           snd            (normalizer snd)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp))))

   (defsynth mod_pulse_s [note 52
                          note_slide 0
                          amp 1
                          amp_slide 0
                          pan 0
                          pan_slide 0
                          attack 0.01
                          sustain 0
                          release 2
                          mod_rate 1
                          mod_rate_slide 0
                          mod_range 5
                          mod_range_slide 0
                          mod_width 0.5
                          mod_width_slide 0
                          pulse_width 0.5
                          pulse_width_slide 0
                          out_bus 0]
     (let [note           (lag note note_slide)
           amp            (lag amp amp_slide)
           pan            (lag pan pan_slide)
           mod_rate       (lag mod_rate mod_rate_slide)
           mod_range      (lag mod_range mod_range_slide)
           mod_width      (lag mod_width mod_width_slide)
           pulse_width    (lag pulse_width pulse_width_slide)
           freq           (midicps note)
           mod_range_freq (- (midicps (+ mod_range note))
                             freq)
           freq-mod       (* mod_range_freq (lf-pulse mod_rate 0.5 mod_width))
           freq           (+ freq freq-mod)
           snd            (pulse freq pulse_width)
           env            (env-gen (env-lin attack sustain release) :action FREE)]
       (out out_bus (pan2 (* env snd) pan amp)))))

  (comment
    (save-to-pi dull_bell)
    (save-to-pi pretty_bell)
    (save-to-pi beep)
    (save-to-pi saw_beep)
    (save-to-pi dsaw)
    (save-to-pi fm)

    (save-to-pi mod_saw)
    (save-to-pi mod_saw_s)
    (save-to-pi mod_dsaw)
    (save-to-pi mod_dsaw_s)
    (save-to-pi mod_sine)
    (save-to-pi mod_sine_s)
    (save-to-pi mod_tri)
    (save-to-pi mod_tri_s)
    (save-to-pi mod_pulse)
    (save-to-pi mod_pulse_s)))

;; Sample playback synths

(without-namespace-in-synthdef

  (defsynth basic_mono_player
    [buf 0
     amp 1
     amp_slide 0
     pan 0
     pan_slide 0
     rate 1
     rate_slide 0
     out_bus 0]
    (let [amp  (lag amp amp_slide)
          pan  (lag pan pan_slide)
          rate (lag rate rate_slide)
          rate (* rate (buf-rate-scale buf))
          snd  (play-buf 1 buf rate :action FREE)]
      (out out_bus (pan2 snd pan  amp))))

  (defsynth basic_stereo_player
    [buf 0
     amp 1
     amp_slide 0
     pan 0
     pan_slide 0
     rate 1
     rate_slide 0
     out_bus 0]
    (let [amp           (lag amp amp_slide)
          pan           (lag pan pan_slide)
          rate          (lag rate rate_slide)
          rate          (* rate (buf-rate-scale buf))
          [snd-l snd-r] (play-buf 2 buf rate :action FREE)
          snd           (balance2 snd-l snd-r pan amp)]
      (out out_bus snd)))

  (defsynth mono_player
    "Plays a mono buffer from start pos to finish pos (represented as
     values between 0 and 1). Outputs a stereo signal."
    [buf 0
     amp 1
     amp_slide 0
     pan 0
     pan_slide 0
     attack 0.0
     sustain -1
     release 0.0
     rate 1
     start 0
     finish 1
     out_bus 0]
    (let [amp         (lag amp amp_slide)
          pan         (lag pan pan_slide)
          n-frames    (- (buf-frames buf) 1)
          start-pos   (* start n-frames)
          end-pos     (* finish n-frames)
          n-start-pos (select:kr (not-pos? rate) [start-pos end-pos])
          n-end-pos   (select:kr (not-pos? rate) [end-pos start-pos])
          rate        (abs rate)
          play-time   (/ (* (buf-dur buf) (absdif finish start))
                         rate)
          phase       (line:ar :start n-start-pos :end n-end-pos :dur play-time :action FREE)
          sustain     (select:kr (= -1 sustain) [sustain (- play-time attack release)])
          env         (env-gen (envelope [0 1 1 0] [attack sustain release]) :action FREE)
          snd         (buf-rd 1 buf phase)
          snd         (* env snd)
          snd         (pan2 snd pan amp)]
      (out out_bus snd)))

  (defsynth stereo_player
    "Plays a mono buffer from start pos to finish pos (represented as
     values between 0 and 1). Outputs a stereo signal."
    [buf 0
     amp 1
     amp_slide 0
     pan 0
     pan_slide 0
     attack 0.0
     sustain -1
     release 0.0
     rate 1
     start 0
     finish 1
     out_bus 0]
    (let [amp           (lag amp amp_slide)
          pan           (lag pan pan_slide)
          n-frames      (- (buf-frames buf) 1)
          start-pos     (* start n-frames)
          end-pos       (* finish n-frames)
          n-start-pos   (select:kr (not-pos? rate) [start-pos end-pos])
          n-end-pos     (select:kr (not-pos? rate) [end-pos start-pos])
          rate          (abs rate)
          play-time     (/ (* (buf-dur buf) (absdif finish start))
                           rate)
          phase         (line:ar :start n-start-pos :end n-end-pos :dur play-time :action FREE)
          sustain       (select:kr (= -1 sustain) [sustain (- play-time attack release)])
          env           (env-gen (envelope [0 1 1 0] [attack sustain release]) :action FREE)
          [snd-l snd-r] (buf-rd 2 buf phase)
          snd           (balance2 snd-l snd-r pan amp)
          snd           (* env snd)]
      (out out_bus snd)))

  (comment
    (save-to-pi mono_player)
    (save-to-pi stereo_player)
    (save-to-pi basic_mono_player)
    (save-to-pi basic_stereo_player)))

(without-namespace-in-synthdef

  (defsynth tb303
    "A simple clone of the sound of a Roland TB-303 bass synthesizer."
    [note     52                        ; midi note value input
     note_slide 0
     amp      1
     amp_slide 0
     pan      0
     pan_slide 0
     attack   0.01
     sustain  0
     release  2
     cutoff   80
     cutoff_slide 0
     cutoff_min 30
     res      0.1                       ; rlpf resonance
     res_slide 0
     wave     0                         ; 0=saw, 1=pulse
     pulse_width 0.5                    ; only for pulse wave
     pulse_width_slide 0
     out_bus  0]
    (let [note            (lag note note_slide)
          amp             (lag amp amp_slide)
          pan             (lag pan pan_slide)
          cutoff_slide    (lag cutoff cutoff_slide)
          res             (lag res res_slide)
          pulse_width     (lag pulse_width pulse_width_slide)
          cutoff-freq     (midicps cutoff)
          cutoff-min-freq (midicps cutoff_min)
          freq            (midicps note)
          env             (env-gen (env-lin attack sustain release) :action FREE)
          snd             (rlpf (select wave [(saw freq) (pulse freq pulse_width)])
                                (+ cutoff-min-freq (* env cutoff-freq))
                                res)
          snd             (* env snd)]
      (out out_bus (pan2 snd pan amp))))

  (defsynth supersaw [note 52
                      note_slide 0
                      amp 1
                      amp_slide 0
                      pan 0
                      pan_slide 0
                      attack 0.01
                      sustain 0
                      release 2
                      cutoff 130
                      cutoff_slide 0
                      res 0.3
                      res_slide 0
                      out_bus 0]
    (let [note        (lag note note_slide)
          amp         (lag amp amp_slide)
          pan         (lag pan pan_slide)
          cutoff      (lag cutoff cutoff_slide)
          res         (lag res res_slide)
          freq        (midicps note)
          cutoff-freq (midicps cutoff)
          input       (lf-saw freq)
          shift1      (lf-saw 4)
          shift2      (lf-saw 7)
          shift3      (lf-saw 5)
          shift4      (lf-saw 2)
          comp1       (> input shift1)
          comp2       (> input shift2)
          comp3       (> input shift3)
          comp4       (> input shift4)
          output      (+ (- input comp1) (- input comp2) (- input comp3) (- input comp4))
          output      (- output input)
          output      (leak-dc:ar (* output 0.25))
          output      (normalizer (rlpf output cutoff-freq res))
          env         (env-gen (env-lin attack sustain release) :action FREE)
          output      (* env output)
          output      (pan2 output pan amp)]
      (out out_bus output)))

  (defsynth supersaw_s [note 52
                        note_slide 0
                        amp 1
                        amp_slide 0
                        pan 0
                        pan_slide 0
                        attack 0.01
                        sustain 0
                        release 2
                        out_bus 0]
    (let [note   (lag note note_slide)
          amp    (lag amp amp_slide)
          pan    (lag pan pan_slide)
          freq   (midicps note)
          input  (lf-saw freq)
          shift1 (lf-saw 4)
          shift2 (lf-saw 7)
          shift3 (lf-saw 2)
          comp1  (> input shift1)
          comp2  (> input shift2)
          comp3  (> input shift3)
          output (+ (- input comp1) (- input comp2) (- input comp3))
          output (- output input)
          output (leak-dc:ar (* output 0.333))
          env    (env-gen (env-lin attack sustain release) :action FREE)
          output (* env output)
          output (pan2 output pan amp)]
      (out out_bus output)))

  (defsynth zawa [note 52
                  note_slide 0
                  amp 1
                  amp_slide 0
                  pan 0
                  pan_slide 0
                  attack 0.1
                  sustain 0
                  release 1
                  cutoff 100
                  cutoff_slide 0
                  rate 1
                  rate_slide 0
                  depth 1.5
                  depth_slide 0
                  out_bus 0]
    (let [note     (lag note note_slide)
          amp      (lag amp amp_slide)
          pan      (lag pan pan_slide)
          wob_rate (lag rate rate_slide)
          cutoff   (lag cutoff cutoff_slide)
          depth    (lag depth depth_slide)
          freq     (midicps note)
          cutoff   (midicps cutoff)

          snd      (lpf (sync-saw
                         freq
                         (* (* freq depth) (+ 2 (sin-osc:kr wob_rate))))
                        cutoff)]
      (out out_bus (* snd
                      (env-gen (envelope [0 1 1 0] [attack sustain release]) :level-scale amp :action FREE)))))


  (defsynth prophet
    "The Prophet Speaks (page 2)

   Dark and swirly, this synth uses Pulse Width Modulation (PWM) to
   create a timbre which continually moves around. This effect is
   created using the pulse ugen which produces a variable width square
   wave. We then control the width of the pulses using a variety of LFOs
   - sin-osc and lf-tri in this case. We use a number of these LFO
   modulated pulse ugens with varying LFO type and rate (and phase in
   some cases to provide the LFO with a different starting point. We
   then mix all these pulses together to create a thick sound and then
   feed it through a resonant low pass filter (rlpf).

   For extra bass, one of the pulses is an octave lower (half the
   frequency) and its LFO has a little bit of randomisation thrown into
   its frequency component for that extra bit of variety."

    [note 52
     note_slide 0
     amp 1
     amp_slide 0
     pan 0
     pan_slide 0
     attack 0.01
     sustain 0
     release 2
     cutoff 110
     cutoff_slide 0
     res 0.3
     res_slide 0
     out_bus 0 ]

    (let [note        (lag note note_slide)
          amp         (lag amp amp_slide)
          pan         (lag pan pan_slide)
          cutoff      (lag cutoff cutoff_slide)
          res         (lag res res_slide)
          freq        (midicps note)
          cutoff-freq (midicps cutoff)
          snd         (mix [(pulse freq (* 0.1 (/ (+ 1.2 (sin-osc:kr 1)) )))
                            (pulse freq (* 0.8 (/ (+ 1.2 (sin-osc:kr 0.3) 0.7) 2)))
                            (pulse freq (* 0.8 (/ (+ 1.2 (lf-tri:kr 0.4 )) 2)))
                            (pulse freq (* 0.8 (/ (+ 1.2 (lf-tri:kr 0.4 0.19)) 2)))
                            (* 0.5 (pulse (/ freq 2) (* 0.8 (/ (+ 1.2 (lf-tri:kr (+ 2 (lf-noise2:kr 0.2))))
                                                               2))))])
          snd         (normalizer snd)
          env         (env-gen (env-lin attack sustain release) :action FREE)
          snd         (rlpf (* env snd snd) cutoff-freq res)
          snd         (pan2 snd pan amp)]

      (out out_bus snd)))

  (comment
    (save-to-pi tb303)
    (save-to-pi supersaw)
    (save-to-pi supersaw_s)
    (save-to-pi prophet)
    (save-to-pi zawa)
        ))

;;FX
(without-namespace-in-synthdef
  (defsynth fx_reverb [mix 0.4
                       mix_slide 0
                       room 0.6
                       room_slide 0
                       damp 0.5
                       damp_slide 0
                       in_bus 0
                       out_bus 0]
    (let [mix   (lag mix mix_slide)
          room  (lag room room_slide)
          damp  (lag damp damp_slide)
          [l r] (in:ar in_bus 2)
          snd   (free-verb2 l r mix room damp)]
      (out out_bus snd)))

  (defsynth fx_replace_reverb [mix 0.4
                               mix_slide 0
                               room 0.6
                               room_slide 0
                               damp 0.5
                               damp_slide 0
                               out_bus 0]
    (let [mix   (lag mix mix_slide)
          room  (lag room room_slide)
          damp  (lag damp damp_slide)
          [l r] (in:ar out_bus 2)
          snd   (free-verb2 l r mix room damp)]
      (replace-out out_bus snd)))



  (defsynth fx_level [amp 1
                      amp_slide 0
                      in_bus 0
                      out_bus 0]
    (let [amp (lag amp amp_slide )]
      (out out_bus (* amp (in in_bus 2)))))

  (defsynth fx_replace_level [amp 1
                              amp_slide 0
                              out_bus 0]
    (let [amp (lag amp amp_slide )]
      (replace-out out_bus (* amp (in out_bus 2)))))


  (defsynth fx_echo
    [delay 0.4
     delay_slide 0
     decay 8
     decay_slide 0
     max_delay 1
     amp 1
     amp_slide 0
     in_bus 0
     out_bus 0]
    (let [delay  (lag delay delay_slide)
          decay  (lag decay decay_slide)
          amp    (lag amp amp_slide)
          source (in in_bus 2)
          echo   (comb-n source max_delay delay decay)]
      (out out_bus (+ echo source))))

  (defsynth fx_replace_echo
    [delay 0.4
     delay_slide 0
     decay 8
     decay_slide 0
     max_delay 1
     amp 1
     amp_slide 0
     out_bus 0]
    (let [delay  (lag delay delay_slide)
          decay  (lag decay decay_slide)
          amp    (lag amp amp_slide)
          source (in out_bus 2)
          echo   (comb-n source max_delay delay decay)]
      (replace-out out_bus (+ echo source))))

  (defsynth fx_slicer
    [rate 4
     rate_slide 0
     width 0.5
     width_slide 0
     phase 0
     amp 1
     amp_slide 0.05
     in_bus 0
     out_bus 0]
    (let [rate      (lag rate rate_slide)
          width     (lag width width_slide)
          amp       (lag amp amp_slide)
          source    (in in_bus 2)
          slice-amp (lag (lf-pulse:kr rate phase width) amp_slide)
          sliced    (* amp slice-amp source)]
      (out out_bus sliced)))

  (defsynth fx_replace_slicer
    [rate 4
     rate_slide 0
     width 0.5
     width_slide 0
     phase 0
     amp 1
     amp_slide 0.05
     out_bus 0]
    (let [rate      (lag rate rate_slide)
          width     (lag width width_slide)
          amp       (lag amp amp_slide)
          source    (in out_bus 2)
          slice-amp (lag (lf-pulse:kr rate phase width) amp_slide)
          sliced    (* amp slice-amp source)]
      (replace-out out_bus sliced)))

  ;; {arg sig     ; RLPF.ar(sig, SinOsc.ar(0.1).exprange(880,12000), 0.2)};

  (defsynth fx_ixi_techno
    [rate 0.1
     rate_slide 0
     cutoff_min 880
     cutoff_min_slide 0
     cutoff_max 12000
     cutoff_max_slide 0
     res 0.2
     res_slide 0
     in_bus 0
     out_bus 0]
    (let [freq (lin-exp (sin-osc rate) -1 1 cutoff_min cutoff_max)
          src  (in in_bus 2)
          src  (rlpf src freq res)]
      (out out_bus src)))

  (defsynth fx_replace_ixi_techno
    [rate 0.1
     rate_slide 0
     cutoff_min 880
     cutoff_min_slide 0
     cutoff_max 12000
     cutoff_max_slide 0
     res 0.2
     res_slide 0
     out_bus 0]
    (let [freq (lin-exp (sin-osc rate) -1 1 cutoff_min cutoff_max)
          src  (in out_bus 2)
          src  (rlpf src freq res)]
      (replace-out out_bus src)))

  (defsynth fx_compressor
    [amp 1
     amp_slide 0
     threshold 0.2
     threshold_slide 0
     clamp_time 0.01
     clamp_time_slide 0
     slope_above 0.5
     slope_above_slide 0
     slope_below 1
     slope_below_slide 0
     relax_time 0.01
     relax_time_slide 0
     in_bus 0
     out_bus 0]
    (let [amp         (lag amp amp_slide)
          threshold   (lag threshold threshold_slide)
          clamp_time  (lag clamp_time clamp_time_slide)
          slope_above (lag slope_above slope_above_slide)
          slope_below (lag slope_below slope_below_slide)
          relax_time  (lag relax_time relax_time_slide)
          src         (* amp (in in_bus 2))]
      (out out_bus (compander src src threshold
                              slope_below slope_above
                              clamp_time relax_time))))

  (defsynth fx_replace_compressor
    [amp 1
     amp_slide 0
     threshold 0.2
     threshold_slide 0
     clamp_time 0.01
     clamp_time_slide 0
     slope_above 0.5
     slope_above_slide 0
     slope_below 1
     slope_below_slide 0
     relax_time 0.01
     relax_time_slide 0
     out_bus 0]
    (let [amp         (lag amp amp_slide)
          threshold   (lag threshold threshold_slide)
          clamp_time  (lag clamp_time clamp_time_slide)
          slope_above (lag slope_above slope_above_slide)
          slope_below (lag slope_below slope_below_slide)
          relax_time  (lag relax_time relax_time_slide)
          src         (* amp (in out_bus 2))]
      (replace-out out_bus (compander src src threshold
                                      slope_below slope_above
                                      clamp_time relax_time))))

  (defsynth fx_rlpf
    [cutoff 100
     cutoff_slide 0
     res 0.6
     res_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in in_bus 2)]
      (out out_bus (rlpf src cutoff res))))

  (defsynth fx_replace_rlpf
    [cutoff 100
     cutoff_slide 0
     res 0.6
     res_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in out_bus 2)]
      (replace-out out_bus (rlpf src cutoff res))))

  (defsynth fx_norm_rlpf
    [cutoff 100
     cutoff_slide 0
     res 0.6
     res_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in in_bus 2)]
      (out out_bus (normalizer (rlpf src cutoff res)))))

  (defsynth fx_replace_norm_rlpf
    [cutoff 100
     cutoff_slide 0
     res 0.6
     res_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in out_bus 2)]
      (replace-out out_bus (normalizer (rlpf src cutoff res)))))

  (defsynth fx_rhpf
    [cutoff 10
     cutoff_slide 0
     res 0.6
     res_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in in_bus 2)]
      (out out_bus (rhpf src cutoff res))))

  (defsynth fx_replace_rhpf
    [cutoff 10
     cutoff_slide 0
     res 0.6
     res_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in out_bus 2)]
      (replace-out out_bus (rhpf src cutoff res))))

  (defsynth fx_norm_rhpf
    [cutoff 10
     cutoff_slide 0
     res 0.6
     res_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in in_bus 2)]
      (out out_bus (normalizer (rhpf src cutoff res)))))

  (defsynth fx_replace_norm_rhpf
    [cutoff 10
     cutoff_slide 0
     res 0.6
     res_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          res    (lag res res_slide)
          src    (in out_bus 2)]
      (replace-out out_bus (normalizer (rhpf src cutoff res)))))

  (defsynth fx_hpf
    [cutoff 10
     cutoff_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in in_bus 2)]
      (out out_bus (hpf src cutoff))))

  (defsynth fx_replace_hpf
    [cutoff 10
     cutoff_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in out_bus 2)]
      (replace-out out_bus (hpf src cutoff))))

  (defsynth fx_norm_hpf
    [cutoff 10
     cutoff_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in in_bus 2)]
      (out out_bus (normalizer (hpf src cutoff)))))

  (defsynth fx_replace_norm_hpf
    [cutoff 10
     cutoff_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in out_bus 2)]
      (replace-out out_bus (normalizer (hpf src cutoff)))))

  (defsynth fx_lpf
    [cutoff 100
     cutoff_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in in_bus 2)]
      (out out_bus (lpf src cutoff))))

  (defsynth fx_replace_lpf
    [cutoff 100
     cutoff_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in out_bus 2)]
      (replace-out out_bus (lpf src cutoff))))

  (defsynth fx_norm_lpf
    [cutoff 100
     cutoff_slide 0
     in_bus 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in in_bus 2)]
      (out out_bus (normalizer (lpf src cutoff)))))

  (defsynth fx_replace_norm_lpf
    [cutoff 100
     cutoff_slide 0
     out_bus 0]
    (let [cutoff (lag cutoff cutoff_slide)
          cutoff (midicps cutoff)
          src    (in out_bus 2)]
      (replace-out out_bus (normalizer (lpf src cutoff)))))

  (defsynth fx_normaliser
    [amp 1
     amp_slide 0
     in_bus 0
     out_bus 0]
    (let [src    (in in_bus 2)]
      (out out_bus (normalizer src amp))))

  (defsynth fx_replace_normaliser
    [amp 1
     amp_slide 0
     out_bus 0]
    (let [src    (in out_bus 2)]
      (replace-out out_bus (normalizer src amp))))

  (defsynth fx_distortion
    [distort 0.5
     distort_slide 0
     in_bus 0
     out_bus 0]
    (let [distort (lag distort distort_slide)
          src     (in in_bus 2)
          k       (/ (* 2 distort) (- 1 distort))
          snd     (/ (* src (+ 1 k)) (+ 1 (* k (abs src))))]
      (out out_bus snd)))

  (defsynth fx_replace_distortion
    [distort 0.5
     distort_slide 0
     out_bus 0]
    (let [distort (lag distort distort_slide)
          src     (in out_bus 2)
          k       (/ (* 2 distort) (- 1 distort))
          snd     (/ (* src (+ 1 k)) (+ 1 (* k (abs src))))]
      (replace-out out_bus snd)))

  (comment
    (save-to-pi fx_reverb)
    (save-to-pi fx_replace_reverb)
    (save-to-pi fx_level)
    (save-to-pi fx_replace_level)
    (save-to-pi fx_echo)
    (save-to-pi fx_replace_echo)
    (save-to-pi fx_slicer)
    (save-to-pi fx_replace_slicer)
    (save-to-pi fx_ixi_techno)
    (save-to-pi fx_replace_ixi_techno)
    (save-to-pi fx_compressor)
    (save-to-pi fx_replace_compressor)
    (save-to-pi fx_rlpf)
    (save-to-pi fx_replace_rlpf)
    (save-to-pi fx_norm_rlpf)
    (save-to-pi fx_replace_norm_rlpf)
    (save-to-pi fx_rhpf)
    (save-to-pi fx_replace_rhpf)
    (save-to-pi fx_norm_rhpf)
    (save-to-pi fx_replace_norm_rhpf)
    (save-to-pi fx_hpf)
    (save-to-pi fx_replace_hpf)
    (save-to-pi fx_norm_hpf)
    (save-to-pi fx_replace_norm_hpf)
    (save-to-pi fx_lpf)
    (save-to-pi fx_replace_lpf)
    (save-to-pi fx_norm_lpf)
    (save-to-pi fx_replace_norm_lpf)
    (save-to-pi fx_normaliser)
    (save-to-pi fx_replace_normaliser)
    (save-to-pi fx_distortion)
    (save-to-pi fx_replace_distortion)))

;; Experimental
(comment
  (do
    ;;TODO FIXME!
    (defsynth babbling [out_bus 0 x 0 y 50]
      (let [x      (abs x)
            x      (/ x 100000)
            x      (min x 0.05)
            x      (max x 0.005)
            y      (abs y)
            y      (min y 10000)
            y      (max y 200)
            noise  (* 0.003
                      (rhpf (one-pole (* 0.99 (brown-noise)))
                            (+ 500 (* 400 (lpf (* (brown-noise) 14))))
                            x))
            noise  [noise noise]
            noise2 (* 0.005
                      (rhpf (one-pole (* 0.99 (brown-noise)))
                            (+ 1000 (* 800 (lpf (* (brown-noise) 20))))
                            x))
            noise2 [noise2 noise2]
            mixed  (+ noise noise2)
            mixed  (lpf mixed y)]
        (out out_bus (* 0 (mix (* 3 mixed))))))
    (save-to-pi babbling))

  (do
    (defsynth woah [note 52 out_bus 0 x 0 y 0]
      (let [freq (midicps note)
            x    (abs x)
            x    (/ x 700)
            x    (min x 15)
            x    (max x 0.5)
            snd  (lpf (sync-saw
                       freq
                       (* (* freq 1.5) (+ 2 (sin-osc:kr x))))
                      1000)]
        (out out_bus (* 0.25 snd))))

    (save-to-pi woah))


  (do
    (defsynth arpeg-click [x 10 buf 0 arp-div 2 beat-div 1 out_bus 0]
      (let [x (abs x)
            x (/ x 70)
            x (min x 200)
            x (max x 1)
            tik   (impulse x)
            a-tik (pulse-divider tik arp-div)
            b-tik (pulse-divider tik beat-div)
            cnt   (mod (pulse-count a-tik) (buf-frames 1))
            note  (buf-rd:kr 1 1 cnt)
            freq  (midicps note)
            snd   (white-noise)
            snd   (rhpf snd 2000 0.4)
            snd   (normalizer snd)
            b-env (env-gen (perc 0.01 0.1) b-tik)
            a-env (env-gen (perc 0.01 0.4) a-tik)]
        (out out_bus (* 0.20 (pan2 (+ (* 0.5 snd b-env)
                                      (* (sin-osc freq) a-env)
                                      (* (sin-osc (* 2 freq)) a-env)))))))

    (save-to-pi arpeg-click) )

  (do
    (defsynth space_organ [note 24 amp 1 x 0 y 0 out_bus 0]
      (let [freq-shift (/ x 100)
            delay (* -1 (/ x 10000))]
        (out out_bus (pan2  (g-verb (* 0.2 (mix (map #(blip (+ freq-shift (* (midicps (duty:kr % 0 (dseq [note (+ 3 note) (+ 7 note) (+ 12 note) (+ 17 note)] INF))) %2)) (mul-add:kr (lf-noise1:kr 1/2) 3 4)) (+ delay [1 1/4]) [1  8]))) 200 8)))))


    (save-to-pi space_organ)

    (defsynth saws [note 52 x 0 y 0 out_bus 0]
      (let [x    (abs x)
            x    (min x 10000)
            x    (max x 50)
            y    (abs y)
            y    (/ y 10000)
            y    (min y 0.3)
            y    (max y 0)
            freq (midicps note)]
        (out out_bus (mix (* 0.15 (normalizer (lpf (saw [freq (+ freq (* freq y))]) x)))))))

    (save-to-pi saws) )

  (mod_dsaw 52))
