package blog.codeninja.rss

import cats.implicits._
import cats.syntax._
import java.io._
import java.nio.file.Path
import monix.execution._
import scala.io.Source

/** Shared functions and constants for any BayesFilter.
  */
object SpamFilter {
  val stopWords = Source.fromFile(getClass.getResource("/filter/stopwords.txt").toURI)
    .getLines
    .toSet

  /** Probability of a message being spam : ham.
    */
  val PrS = 0.5
  val PrH = 1.0 - PrS

  /** Implicit for splitting a text string into words, etc.
    */
  implicit class Splitter(s: String) {
    def words = s.split("""[\p{P}\s]+""") filterNot (stopWords.contains _)
  }
}

/**
  */
class SpamFilter(val source: Path, val archive: Archive) {
  import SpamFilter.Splitter
  import Scheduler.Implicits.global

  private var spamWords = Map[String, Double]()
  private var n = 0

  // whenever the archive gets a new headline, update the filter
  archive foreach {
    hs => hs.headOption foreach (spam _)
  }

  // attempt to load the existing filter
  if (source.toFile.exists) {
    val ois = new ObjectInputStream(new FileInputStream(source.toString))

    // read the filter data
    val spamWords = ois.readObject.asInstanceOf[Map[String, Double]]
    val n = ois.readInt

    // close the stream
    ois.close
  }

  /** Write the filter to disk.
    */
  def save: Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(source.toString))

    // write the filter data
    oos.writeObject(spamWords)
    oos.writeInt(n)

    // done
    oos.close
  }

  /** Add a headline to the spam filter by putting all the title and summary text into it.
    */
  def spam(h: Headline): Unit = {
    val words = h.title.words ++ h.body.words

    // traverse all the words and add them to the map
    val map = words.map(_ -> 1.0).toMap

    // tally the number of headlines marked as spam
    spamWords = spamWords |+| map
    n += 1
  }

  /** Calculates the probability that a headlines should be filtered.
    */
  def filter(h: Headline): Double = {
    val words = h.title.words ++ h.body.words

    // calculate the probabilty of each word being spam
    val prws = for (word <- words) yield {
      if (n == 0) 0 else spamWords.getOrElse(word, 0.0) / n
    }

    // numerator (p._1) and demoninator (p._2)
    val prms = prws.foldLeft((1.0, 1.0)) {
      (p, w) => (p._1 * w, p._2 * (1.0 - w))
    }

    // calculate the probabilty that this headline is spam
    prms._1 / (prms._1 + prms._2)
  }
}
