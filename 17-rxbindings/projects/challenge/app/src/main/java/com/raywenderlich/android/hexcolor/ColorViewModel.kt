/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.raywenderlich.android.hexcolor

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.raywenderlich.android.hexcolor.networking.ColorApi
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject

class ColorViewModel(
    backgroundScheduler: Scheduler,
    mainScheduler: Scheduler,
    colorApi: ColorApi,
    colorCoordinator: ColorCoordinator
) : ViewModel() {

    private val backStream = PublishSubject.create<Unit>()
    private val clearStream = PublishSubject.create<Unit>()
    private val digitsStream = BehaviorSubject.create<String>()

    val hexStringSubject = BehaviorSubject.createDefault("#")
    val hexStringLiveData = MutableLiveData<String>()
    val backgroundColorLiveData = MutableLiveData<Int>()
    val rgbStringLiveData = MutableLiveData<String>()
    val colorNameLiveData = MutableLiveData<String>()

    private val disposables = CompositeDisposable()

    init {
        hexStringSubject
            .subscribeOn(backgroundScheduler)
            .observeOn(mainScheduler)
            .subscribe(hexStringLiveData::postValue)
            .addTo(disposables)

        hexStringSubject
            .subscribeOn(backgroundScheduler)
            .observeOn(mainScheduler)
            .map { if (it.length < 7) "#000000" else it }
            .map { colorCoordinator.parseColor(it) }
            .subscribe(backgroundColorLiveData::postValue)
            .addTo(disposables)

        hexStringSubject
            .filter { it.length == 7 }
            .observeOn(mainScheduler)
            .flatMapSingle {
                colorApi.getClosestColor(it).subscribeOn(backgroundScheduler)
            }
            .map { it.name.value }
            .subscribe(colorNameLiveData::postValue)
            .addTo(disposables)

        hexStringSubject
            .subscribeOn(backgroundScheduler)
            .observeOn(mainScheduler)
            .filter { it.length < 7 }
            .map { "--" }
            .subscribe(colorNameLiveData::postValue)
            .addTo(disposables)

        hexStringSubject
            .subscribeOn(backgroundScheduler)
            .observeOn(mainScheduler)
            .map {
                if (it.length == 7) {
                    colorCoordinator.parseRgbColor(it)
                } else {
                    RGBColor(255, 255, 255)
                }
            }
            .map { "${it.red},${it.green},${it.blue}" }
            .subscribe(rgbStringLiveData::postValue)
            .addTo(disposables)

        clearStream
            .map { "#" }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)

        backStream
            .map { currentHexValue() }
            .filter { it.length >= 2 }
            .map { it.substring(0, currentHexValue().lastIndex) }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)

        digitsStream
            .map { it to currentHexValue() }
            .filter { it.second.length < 7 }
            .map { currentHexValue() + it.first }
            .subscribe(hexStringSubject::onNext)
            .addTo(disposables)
    }

    fun backClicked() = backStream.onNext(Unit)

    fun clearClicked() = clearStream.onNext(Unit)

    fun digitClicked(digit: String) = digitsStream.onNext(digit)

    private fun currentHexValue(): String {
        return hexStringSubject.value ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}
