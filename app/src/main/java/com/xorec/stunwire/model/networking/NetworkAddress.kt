package com.xorec.stunwire.model.networking

import java.net.InetAddress

data class NetworkAddress(val address: InetAddress, val port: Int) {
    constructor(string: String) : this(InetAddress.getByName(string.split(":")[0]), string.split(":")[1].toInt())


    override fun toString(): String {
        return address.toString().replace("/", "") + ":" + port.toString()
    }
}